#!/usr/bin/env python3
"""
affected_modules.py — compute the set of modules affected by a git diff.

Algorithm
---------
1. `git diff --name-only $BASE...HEAD` → list of changed files.
2. Map each file to its owning Gradle module by walking up its path
   and matching against the `projectDir` entries in
   `docs/dependency-graph.json`.
3. Reverse the dependency edges (`A → B` becomes `B → A`) so we can
   ask: "who depends on the changed modules?".
4. BFS in the reversed graph starting from `changedModules`. The
   transitive closure is `affectedModules`.
5. Print a JSON report containing `changedModules`, `affectedModules`,
   and `suggestedGradleTasks` (assembleDebug / test tasks).

Usage
-----
    python3 tools/affected-modules/affected_modules.py --base main
    python3 tools/affected-modules/affected_modules.py --base origin/main \\
        --graph docs/dependency-graph.json

The graph file must be generated first:
    ./gradlew generateDependencyGraph
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from collections import defaultdict, deque
from pathlib import Path


def repo_root() -> Path:
    """Return the absolute path to the repository root (= cwd of git)."""
    out = subprocess.check_output(
        ["git", "rev-parse", "--show-toplevel"], text=True
    ).strip()
    return Path(out)


def git_changed_files(base: str) -> list[str]:
    """All files changed between `base` and HEAD (committed + uncommitted)."""
    # Committed changes between base and HEAD.
    committed = subprocess.check_output(
        ["git", "diff", "--name-only", f"{base}...HEAD"], text=True
    ).splitlines()
    # Unstaged + staged changes on top of HEAD.
    uncommitted = subprocess.check_output(
        ["git", "diff", "--name-only", "HEAD"], text=True
    ).splitlines()
    untracked = subprocess.check_output(
        ["git", "ls-files", "--others", "--exclude-standard"], text=True
    ).splitlines()
    return sorted({f for f in committed + uncommitted + untracked if f})


def load_graph(graph_path: Path) -> list[dict]:
    if not graph_path.exists():
        sys.exit(
            f"[affected_modules] dependency graph not found: {graph_path}\n"
            f"Run `./gradlew generateDependencyGraph` first."
        )
    return json.loads(graph_path.read_text())["modules"]


def module_for_file(file_path: str, modules: list[dict]) -> str | None:
    """Return the Gradle path of the module that owns `file_path`, or None."""
    # Sort by projectDir length descending so nested paths (e.g.
    # `features/login`) are matched before parents (e.g. `features`).
    candidates = sorted(modules, key=lambda m: len(m["projectDir"]), reverse=True)
    norm = file_path.replace("\\", "/")
    for m in candidates:
        prefix = m["projectDir"].rstrip("/") + "/"
        if prefix == "/" or norm == m["projectDir"] or norm.startswith(prefix):
            return m["path"]
    return None


def build_reverse_graph(modules: list[dict]) -> dict[str, set[str]]:
    """`A → B` becomes `B → A` so we can find dependents."""
    reverse: dict[str, set[str]] = defaultdict(set)
    for m in modules:
        for dep in m["dependencies"]:
            reverse[dep].add(m["path"])
    return reverse


def transitive_dependents(seeds: set[str], reverse: dict[str, set[str]]) -> set[str]:
    """BFS from `seeds` through reversed edges."""
    visited: set[str] = set(seeds)
    queue: deque[str] = deque(seeds)
    while queue:
        node = queue.popleft()
        for parent in reverse.get(node, ()):
            if parent not in visited:
                visited.add(parent)
                queue.append(parent)
    return visited


def suggest_tasks(affected: set[str]) -> list[str]:
    """Suggest Gradle tasks per affected module."""
    tasks: list[str] = []
    for path in sorted(affected):
        # `:app` builds an APK; everything else is a library.
        if path == ":app":
            tasks.append(f"{path}:assembleDebug")
            tasks.append(f"{path}:testDebugUnitTest")
        else:
            tasks.append(f"{path}:assembleDebug")
            tasks.append(f"{path}:testDebugUnitTest")
    return tasks


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--base",
        default="main",
        help="Base git ref to diff against (default: main)",
    )
    parser.add_argument(
        "--graph",
        default="docs/dependency-graph.json",
        help="Path to dependency-graph.json (default: docs/dependency-graph.json)",
    )
    parser.add_argument(
        "--compact",
        action="store_true",
        help="Emit single-line JSON instead of the default pretty form.",
    )
    parser.add_argument(
        "--pretty",
        action="store_true",
        help="(deprecated) kept for backwards compatibility — pretty is the default.",
    )
    args = parser.parse_args()

    root = repo_root()
    os.chdir(root)

    graph_path = (root / args.graph).resolve()
    modules = load_graph(graph_path)

    changed_files = git_changed_files(args.base)

    changed_modules: set[str] = set()
    unmapped_files: list[str] = []
    for f in changed_files:
        m = module_for_file(f, modules)
        if m is None:
            unmapped_files.append(f)
        else:
            changed_modules.add(m)

    reverse = build_reverse_graph(modules)
    affected = transitive_dependents(changed_modules, reverse)
    tasks = suggest_tasks(affected)

    report = {
        "base": args.base,
        "changedFiles": changed_files,
        "unmappedFiles": unmapped_files,
        "changedModules": sorted(changed_modules),
        "affectedModules": sorted(affected),
        "suggestedGradleTasks": tasks,
    }
    print(json.dumps(report, indent=None if args.compact else 2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
