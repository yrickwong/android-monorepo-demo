# AGENTS.md — Project ground rules for AI agents & humans

This file is the **single entry point** for any AI coding agent (Codewiz, Claude
Code, Cursor, Codex, Aider…) working in this repository. Humans should read it
too — every rule applies to both.

If you only read one section, read **§ Rule 0: Docs-sync contract**.

---

## Rule 0: Docs-sync contract (MANDATORY)

> **Whenever you ship a design change or commit code, you MUST update the
> matching documentation in the same commit or PR.**

The "what triggers what" mapping is **declared, not folklore** — it lives in
[`tools/docs-sync/docs-sync-rules.json`](tools/docs-sync/docs-sync-rules.json)
and is enforced by:

- [`tools/docs-sync/check_docs_sync.py`](tools/docs-sync/check_docs_sync.py)
  (CI runs it with `--strict`; the build fails on any violation)
- A human-readable companion at
  [`docs/doc-sync-rules.md`](docs/doc-sync-rules.md) explaining each rule

### Quick reference (the 8 current rules)

| ID | If you change …                                                          | You MUST also update …                                          |
| --- | --- | --- |
| R1 | `foundations/assemblekit/src/main/**`                                    | `docs/architecture.md` (§ AssembleKit / v2)                     |
| R2 | `…/CheckDependencyRulesTask.kt`                                          | `docs/module-rules.md`                                          |
| R3 | `settings.gradle.kts` (any `include(...)` change)                        | `docs/architecture.md` + `docs/module-rules.md` + `README.md`   |
| R4 | `foundations/router/.../Router.kt`                                       | `docs/architecture.md`                                          |
| R5 | `.github/workflows/*.yml`                                                | `docs/architecture.md` or `README.md`                           |
| R6 | New `tools/**/*.{py,sh}`                                                 | `docs/architecture.md` or `README.md` or `tools/**/README.md`   |
| R7 | New / renamed `*ConventionPlugin.kt`                                     | `docs/architecture.md`                                          |
| R8 | `gradle/libs.versions.toml` (new library / new version)                  | `docs/architecture.md`                                          |

> The table above is informational. The **source of truth** is the JSON file.
> If the JSON and this README ever disagree, the JSON wins — but you must fix
> this README in the same commit (R0 itself: meta-rule).

### Workflow

```bash
# Before you push / open a PR:
python3 tools/docs-sync/check_docs_sync.py --base origin/main

# Optional, opt-in local guardrail (warns on every commit, never blocks):
bash tools/docs-sync/install-hooks.sh
```

### Escape hatch (genuinely n/a changes)

If a change really doesn't deserve a doc update (pure rename, dead-code
removal, version bump with no surface change…), add **one** of these to a
commit message in the PR:

- `[docs-skip]` — skip every rule
- `[docs-skip:R3-new-module]` — skip a single rule by id

You must justify the skip in the PR description. CI will trust the marker but
reviewers should not.

### When the rules themselves change

Editing
[`tools/docs-sync/docs-sync-rules.json`](tools/docs-sync/docs-sync-rules.json)
counts as a design change. In the same commit:

1. Update the quick-reference table above
2. Update [`docs/doc-sync-rules.md`](docs/doc-sync-rules.md)
3. Run `python3 tools/docs-sync/check_docs_sync.py --base origin/main` to
   self-verify

---

## Rule 1: Layered module boundaries

Five layers, downward-only dependencies, enforced by
`./gradlew checkDependencyRules`. Full details in
[`docs/module-rules.md`](docs/module-rules.md).

```
:app  →  :features:*  →  :bizlibs:*  →  :foundations:*  →  :third-party:*
```

- `:features:*` never depend on each other — cross-feature jumps go through
  `:foundations:router`
- `:bizlibs:*` never depend on `:features:*`
- `:foundations:*` never depend on `:bizlibs:*` / `:features:*`
- `:third-party:*` never depend on any business module

Violating any of these fails the `checkDependencyRules` task **and** CI.

---

## Rule 2: AssembleKit is the page-level framework

All page-shaped UI (Activity / Fragment-replacement) MUST use
[`:foundations:assemblekit`](foundations/assemblekit). Concretely:

- Hosts extend `PageHostActivity` (or implement `PageHost`)
- Pages extend `ViewPage` today (`ComposablePage` once the Compose module
  ships)
- Per-page state lives in `PageViewModel<S : MavericksState>` — this is the
  MVI contract; do NOT bypass it with raw `StateFlow` in Pages
- Cross-Page comms inside one Assembly go through `ScopedEventBus` /
  `ScopedCommandBus`; cross-layer data goes through `provides` / `consume`
- Structural changes (`assembly.replace { }`) are **host-only** — a Page
  cannot reach the Assembly handle

Design rationale lives in
[`docs/architecture.md` § AssembleKit](docs/architecture.md#页面装配框架assemblekit)
and
[§ AssembleKit v2](docs/architecture.md#assemblekit-v2列表上下文多槽位host-驱动-replace).

---

## Rule 3: Commit style

- Atomic commits, one logical change per commit
- Conventional commits header: `<type>(<scope>): <subject>`
  - `feat` / `fix` / `refactor` / `docs` / `chore` / `build` / `ci` / `test`
- Body explains *why*, not *what* — the diff already shows *what*
- A commit that violates Rule 0 will be flagged by CI; rebase and amend

---

## Rule 4: Tooling

| Task | Command |
| --- | --- |
| Build everything | `./gradlew assembleDebug` |
| Enforce layering | `./gradlew checkDependencyRules` |
| Refresh dep graph | `./gradlew generateDependencyGraph` |
| Compute affected modules | `python3 tools/affected-modules/affected_modules.py --base origin/main` |
| Check docs sync | `python3 tools/docs-sync/check_docs_sync.py --base origin/main` |

All four are wired into `.github/workflows/ci.yml`.

---

## For AI agents specifically

- **Do not invent files or symbols.** Read before you edit.
- **Do not skip Rule 0 silently.** If your change matches a trigger, either
  update the docs in the same commit/PR, or use the explicit
  `[docs-skip:<id>]` marker with a justification — never just hope CI
  doesn't notice.
- **Match existing style.** Kotlin conventions live in the surrounding code;
  Python scripts mirror
  [`tools/affected-modules/affected_modules.py`](tools/affected-modules/affected_modules.py)
  (argparse + git diff + JSON output).
- **Prefer modifying existing files** over creating new ones; create new docs
  only when the rule table demands it.
