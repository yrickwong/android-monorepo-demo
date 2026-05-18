#!/usr/bin/env python3
"""
check_docs_sync.py — enforce the "code change → doc update" contract.

Reads the declarative rules in `tools/docs-sync/docs-sync-rules.json` and the
git diff between a base ref (default: `origin/main`) and HEAD (plus any
unstaged/staged/untracked changes), then reports any rule whose triggers
matched but whose required docs were NOT updated in the same set.

Usage
-----
    # Local pre-push sanity check (warns, exit 0).
    python3 tools/docs-sync/check_docs_sync.py --base origin/main

    # CI gate — fails the build (exit 1) on any violation.
    python3 tools/docs-sync/check_docs_sync.py --base origin/main --strict

    # Machine-readable.
    python3 tools/docs-sync/check_docs_sync.py --json

Escape hatches
--------------
Include either marker in any commit message between base...HEAD:
    [docs-skip]                 # skip everything
    [docs-skip:R3-new-module]   # skip a single rule by id

Design notes
------------
* Pure stdlib — runs anywhere Python 3.8+ is available, no PyYAML / no pip.
* Mirrors the style of tools/affected-modules/affected_modules.py:
  argparse → git diff → JSON-friendly report → exit code policy.
* Globs use fnmatch semantics with one extension: '**' is treated as
  "match any number of path segments" (including zero).
"""

from __future__ import annotations

import argparse
import fnmatch
import json
import os
import re
import subprocess
import sys
from pathlib import Path
from typing import Iterable


# ─────────────────────────────────────────────────────────────────────────────
# Git helpers
# ─────────────────────────────────────────────────────────────────────────────

def repo_root() -> Path:
    out = subprocess.check_output(
        ["git", "rev-parse", "--show-toplevel"], text=True
    ).strip()
    return Path(out)


def _git(*args: str) -> str:
    try:
        return subprocess.check_output(["git", *args], text=True)
    except subprocess.CalledProcessError:
        return ""


def changed_files(base: str) -> tuple[list[str], list[str]]:
    """Return (committed_files, working_tree_files) since `base`."""
    committed = _git("diff", "--name-only", f"{base}...HEAD").splitlines()
    staged = _git("diff", "--name-only", "--cached").splitlines()
    unstaged = _git("diff", "--name-only", "HEAD").splitlines()
    untracked = _git(
        "ls-files", "--others", "--exclude-standard"
    ).splitlines()
    working = sorted({f for f in staged + unstaged + untracked if f})
    return sorted({f for f in committed if f}), working


def added_files(base: str) -> set[str]:
    """Files added (status A) between base and HEAD, or new in working tree."""
    out = _git("diff", "--name-status", f"{base}...HEAD").splitlines()
    added = {line.split("\t", 1)[1] for line in out if line.startswith("A\t")}
    added |= set(_git("ls-files", "--others", "--exclude-standard").splitlines())
    return added


def renamed_files(base: str) -> set[str]:
    """New names of files renamed (status R) between base and HEAD."""
    out = _git("diff", "--name-status", "-M", f"{base}...HEAD").splitlines()
    renamed: set[str] = set()
    for line in out:
        if line.startswith("R"):
            parts = line.split("\t")
            if len(parts) >= 3:
                renamed.add(parts[2])
    return renamed


def commit_messages(base: str) -> list[str]:
    body = _git("log", f"{base}..HEAD", "--pretty=%B")
    return [m for m in body.split("\n") if m]


def diff_text_for_files(base: str, files: Iterable[str]) -> str:
    rel = [f for f in files if f]
    if not rel:
        return ""
    out = _git("diff", f"{base}...HEAD", "--", *rel)
    out += "\n" + _git("diff", "HEAD", "--", *rel)
    out += "\n" + _git("diff", "--cached", "--", *rel)
    return out


# ─────────────────────────────────────────────────────────────────────────────
# Glob with '**'
# ─────────────────────────────────────────────────────────────────────────────

def _glob_to_regex(pattern: str) -> re.Pattern[str]:
    """Translate a path glob with '**' into a regex.

    '**' matches across path separators (any number of segments, including 0);
    '*' matches within a single segment; '?' matches a single char.
    """
    i, n = 0, len(pattern)
    out: list[str] = ["^"]
    while i < n:
        c = pattern[i]
        if c == "*":
            if i + 1 < n and pattern[i + 1] == "*":
                # consume ** and optional trailing slash
                j = i + 2
                if j < n and pattern[j] == "/":
                    j += 1
                out.append(".*")
                i = j
                continue
            out.append("[^/]*")
            i += 1
        elif c == "?":
            out.append("[^/]")
            i += 1
        elif c in ".+()|^$\\{}[]":
            out.append(re.escape(c))
            i += 1
        else:
            out.append(c)
            i += 1
    out.append("$")
    return re.compile("".join(out))


def matches_any(path: str, patterns: list[str]) -> bool:
    for p in patterns:
        if _glob_to_regex(p).match(path):
            return True
    return False


# ─────────────────────────────────────────────────────────────────────────────
# Rule engine
# ─────────────────────────────────────────────────────────────────────────────

class Violation:
    def __init__(self, rule: dict, triggered_by: list[str], missing: list[str]):
        self.rule = rule
        self.triggered_by = triggered_by
        self.missing = missing

    def to_dict(self) -> dict:
        return {
            "id": self.rule["id"],
            "title": self.rule["title"],
            "rationale": self.rule.get("rationale", ""),
            "triggeredBy": sorted(self.triggered_by),
            "requiredButMissing": sorted(self.missing),
        }


def evaluate(
    rules: list[dict],
    changed: set[str],
    added: set[str],
    renamed: set[str],
    base: str,
    skip_global: bool,
    skip_ids: set[str],
) -> list[Violation]:
    violations: list[Violation] = []
    if skip_global:
        return violations

    for rule in rules:
        if rule["id"] in skip_ids:
            continue

        triggers = rule.get("triggers", [])
        exempt = rule.get("exempt_paths", [])

        # Find triggering files (subject to exemptions).
        triggering = [
            f for f in changed
            if matches_any(f, triggers) and not matches_any(f, exempt)
        ]

        # only_when_added* gates: only consider new/renamed files.
        if rule.get("only_when_added") and triggering:
            triggering = [f for f in triggering if f in added]
        if rule.get("only_when_added_or_renamed") and triggering:
            triggering = [f for f in triggering if f in added or f in renamed]

        # diff_must_contain gates: only fire when the diff text contains the
        # marker, e.g. settings.gradle.kts only counts if `include(` was added.
        markers: list[str] = []
        if "diff_must_contain" in rule:
            markers.append(rule["diff_must_contain"])
        if "diff_must_contain_any_of" in rule:
            markers.extend(rule["diff_must_contain_any_of"])
        if triggering and markers:
            diff = diff_text_for_files(base, triggering)
            if not any(m in diff for m in markers):
                triggering = []

        if not triggering:
            continue

        # Required docs.
        ok = False
        if "requires_any_of" in rule:
            ok = any(
                matches_any(f, [pat]) for pat in rule["requires_any_of"]
                for f in changed
            )
            missing = rule["requires_any_of"] if not ok else []
        elif "requires_all_of" in rule:
            missing = [
                pat for pat in rule["requires_all_of"]
                if not any(matches_any(f, [pat]) for f in changed)
            ]
            ok = not missing
        else:
            ok = True
            missing = []

        if not ok:
            violations.append(Violation(rule, triggering, missing))

    return violations


# ─────────────────────────────────────────────────────────────────────────────
# Reporting
# ─────────────────────────────────────────────────────────────────────────────

def print_human(violations: list[Violation], strict: bool) -> None:
    if not violations:
        print("[docs-sync] OK — every triggered rule has matching doc updates.")
        return

    print(
        f"[docs-sync] {'FAILED' if strict else 'WARNING'} — "
        f"{len(violations)} rule(s) need doc updates:\n"
    )
    for i, v in enumerate(violations, 1):
        print(f"  {i}. [{v.rule['id']}] {v.rule['title']}")
        print(f"     why     : {v.rule.get('rationale', '')}")
        print("     triggered by:")
        for f in sorted(v.triggered_by):
            print(f"        - {f}")
        print("     please also update ANY of:")
        for f in sorted(v.missing):
            print(f"        - {f}")
        print(
            "     (use '[docs-skip:" + v.rule["id"] + "]' in a commit message "
            "if this is genuinely a no-op for docs; justify in PR.)\n"
        )

    if strict:
        print(
            "[docs-sync] Run without --strict locally to keep iterating; CI "
            "will keep failing until every rule above is either resolved or "
            "explicitly skipped."
        )


# ─────────────────────────────────────────────────────────────────────────────
# Entrypoint
# ─────────────────────────────────────────────────────────────────────────────

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default="origin/main",
                        help="Base git ref to diff against (default: origin/main).")
    parser.add_argument(
        "--rules",
        default="tools/docs-sync/docs-sync-rules.json",
        help="Path to docs-sync-rules.json.",
    )
    parser.add_argument("--strict", action="store_true",
                        help="Exit with code 1 on any violation (CI mode).")
    parser.add_argument("--json", action="store_true",
                        help="Emit a machine-readable JSON report on stdout.")
    args = parser.parse_args()

    root = repo_root()
    os.chdir(root)

    rules_path = (root / args.rules).resolve()
    if not rules_path.exists():
        sys.exit(f"[docs-sync] rules file not found: {rules_path}")
    rules = json.loads(rules_path.read_text())["rules"]

    # Fall back to merge-base resolution if the literal ref does not exist
    # (e.g. fresh clone without origin/main fetched).
    base = args.base
    if subprocess.call(
        ["git", "rev-parse", "--verify", "--quiet", base],
        stdout=subprocess.DEVNULL,
    ) != 0:
        # Try common fallbacks.
        for cand in ("main", "HEAD~1"):
            if subprocess.call(
                ["git", "rev-parse", "--verify", "--quiet", cand],
                stdout=subprocess.DEVNULL,
            ) == 0:
                base = cand
                break

    committed, working = changed_files(base)
    changed = set(committed) | set(working)
    added = added_files(base) | set(working)
    renamed = renamed_files(base)

    # Skip markers.
    msgs = commit_messages(base)
    skip_global = any("[docs-skip]" in m for m in msgs)
    skip_ids = set(re.findall(r"\[docs-skip:([A-Za-z0-9_\-]+)\]", "\n".join(msgs)))

    violations = evaluate(
        rules,
        changed=changed,
        added=added,
        renamed=renamed,
        base=base,
        skip_global=skip_global,
        skip_ids=skip_ids,
    )

    if args.json:
        report = {
            "base": base,
            "changedFiles": sorted(changed),
            "skipGlobal": skip_global,
            "skipIds": sorted(skip_ids),
            "violations": [v.to_dict() for v in violations],
        }
        print(json.dumps(report, indent=2))
    else:
        print_human(violations, args.strict)

    if args.strict and violations:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
