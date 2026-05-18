# 文档同步规则（Docs-sync rules）

> 这是 [`AGENTS.md` § Rule 0](../AGENTS.md#rule-0-docs-sync-contract-mandatory) 的人类可读伴随文档。
> **机器可读的真源**是 [`tools/docs-sync/docs-sync-rules.json`](../tools/docs-sync/docs-sync-rules.json)，校验由 [`tools/docs-sync/check_docs_sync.py`](../tools/docs-sync/check_docs_sync.py) 执行。
> 两份不一致时**以 JSON 为准**，并须在同一个 commit 里把这份 md 修对（R0 自身的元规则）。

## 为什么需要这条规则

代码与文档脱节是规模化工程里最常见、也最贵的腐烂方式。一旦设计文档、模块图、CI 流程跟实现不对得上，新人会被错误的"上下文"误导、老人会逐渐放弃维护文档。本规则的目的是：

- **把"做完功能要不要写文档"变成机械问题**：触发某些路径变更 → 必须同步对应文档，不再凭自觉
- **由 CI 强制执行**：违反就 fail PR，避免"反正 reviewer 不一定注意到"
- **同时给 AI agent 一个明确指令**：`AGENTS.md` 是事实标准入口文件，Codewiz / Claude Code / Cursor / Codex / Aider 都会读

## 规则总览

8 条规则，按 ID 排列：

### R1 — AssembleKit framework changes must update architecture.md

| 触发 | `foundations/assemblekit/src/main/**/*.kt` |
| --- | --- |
| 必须同步 | `docs/architecture.md`（任一即可） |
| 豁免 | `foundations/assemblekit/src/test/**`、`foundations/assemblekit/src/androidTest/**` |
| 为什么 | 框架的公开 API（Page / ViewPage / Assembly / ListPage / scoped locals…）和行为契约都登记在 architecture.md 的 § AssembleKit 与 § AssembleKit v2 节。任何源码改动——不论是新增 API、改默认行为、还是修关键 bug——都必须让那一节同步。 |

### R2 — Dependency-rule task changes must update module-rules.md

| 触发 | `build-logic/convention/src/main/kotlin/com/demo/monorepo/buildlogic/CheckDependencyRulesTask.kt` |
| --- | --- |
| 必须同步 | `docs/module-rules.md`（任一即可） |
| 为什么 | `CheckDependencyRulesTask.kt` 是分层校验的唯一真源。改这个文件意味着规则边界本身发生了变化，必须同步到 module-rules.md 的规则总览 / 矩阵 / 示例三处。 |

### R3 — Gradle module include changes must update structure docs

| 触发 | `settings.gradle.kts`（diff 中必须包含 `include(`） |
| --- | --- |
| 必须同步 | `docs/architecture.md` **且** `docs/module-rules.md` **且** `README.md`（三份全部要） |
| 为什么 | 新增 / 删除 module 会影响：分层图（architecture.md）、合法依赖示例（module-rules.md）、demo 走读（README.md）。三处不同步就一定会有一处变成谎话。 |
| 备注 | 仅当 diff 含 `include(` 字面量时触发；纯改 settings 里其它配置（比如 plugins 仓库）不触发。 |

### R4 — Router path table changes must update architecture.md

| 触发 | `foundations/router/src/main/java/com/demo/foundations/router/Router.kt` |
| --- | --- |
| 必须同步 | `docs/architecture.md`（任一即可） |
| 为什么 | `Router.Paths` 是 feature 之间的公共契约，是 module-rules R1（"feature 不可互相依赖"）能成立的工具。新增一条路径 = 新增一个对外接口。 |

### R5 — CI workflow changes must update architecture.md or README

| 触发 | `.github/workflows/*.yml` / `*.yaml` |
| --- | --- |
| 必须同步 | `docs/architecture.md` **或** `README.md` |
| 为什么 | CI step 的增/删/改顺序对读者理解工程的反馈循环非常重要；文档不更新会让新人误以为某个检查不存在。 |

### R6 — New tooling scripts must be documented

| 触发 | 新增 `tools/**/*.py` 或 `tools/**/*.sh`（仅"新增"，已有脚本改动不触发） |
| --- | --- |
| 必须同步 | `docs/architecture.md` **或** `README.md` **或** `tools/**/README.md` |
| 为什么 | 每个新工具都是工程表面的一部分；不被任何 md 提及就等于不存在。允许放在 `tools/<name>/README.md` 里就近说明，避免逼着所有人都去改主文档。 |

### R7 — New / renamed Convention Plugins must update architecture.md

| 触发 | 新增或重命名 `build-logic/convention/src/main/kotlin/com/demo/monorepo/buildlogic/*ConventionPlugin.kt` |
| --- | --- |
| 必须同步 | `docs/architecture.md`（任一即可） |
| 为什么 | Convention Plugin ID（`demo.android.application`、`.feature`、`.foundation`、`.bizlib`…）会被 architecture.md 逐字引用。换名 / 加新插件却不改文档 = 文档失效。 |

### R8 — New libraries / version-catalog additions must update architecture.md

| 触发 | `gradle/libs.versions.toml`，且 diff 包含 `[libraries]` 或 `[versions]` 段落改动 |
| --- | --- |
| 必须同步 | `docs/architecture.md`（任一即可） |
| 为什么 | 新增一个三方库（recyclerview、mavericks、未来的 compose…）是技术栈级决策，必须出现在 architecture.md 的技术栈/依赖说明里。纯改 buildToolsVersion 之类不触发（不在 `[libraries]` / `[versions]` 之内）。 |

## 用法

### 本地运行（默认非阻断）

```bash
python3 tools/docs-sync/check_docs_sync.py --base origin/main
```

输出例：

```
[docs-sync] WARNING — 1 rule(s) need doc updates:

  1. [R1-assemblekit-api] AssembleKit framework changes must update architecture.md
     why     : Public Page / ViewPage / Assembly / ListPage / scoped locals API and behaviour are documented in architecture.md (§ AssembleKit, § AssembleKit v2). Any source change in this folder MUST be reflected there.
     triggered by:
        - foundations/assemblekit/src/main/java/com/demo/foundations/assemblekit/Page.kt
     please also update ANY of:
        - docs/architecture.md
     (use '[docs-skip:R1-assemblekit-api]' in a commit message if this is genuinely a no-op for docs; justify in PR.)
```

### CI 模式（违反即 fail）

```bash
python3 tools/docs-sync/check_docs_sync.py --base origin/main --strict
```

非零退出码会让 GitHub Actions 红勾变红叉。

### 机器可读

```bash
python3 tools/docs-sync/check_docs_sync.py --base origin/main --json
```

适合接到其它工具（review bot / dashboard）里。

### 可选：本地 post-commit 提示

```bash
bash tools/docs-sync/install-hooks.sh
```

装完每次 `git commit` 后立刻看到提示，但**永远不阻断**提交——避免变成"本地一改提交就报错"的体验灾难。CI 才是真正的卡口。

## 豁免（escape hatch）

某些改动确实不该触发文档同步（纯重命名、死代码删除、不影响 surface 的版本号 bump…）。在 PR 内任意一个 commit message 中加入：

- `[docs-skip]` — 跳过**所有**规则
- `[docs-skip:R3-new-module]` — 仅跳过指定规则

校验器会把所有 commit message 串起来扫描标记。**必须在 PR 描述里说明跳过理由**，CI 信任标记但 reviewer 不应该。

## 规则本身怎么演化

新增 / 删除 / 修改一条规则就是一次设计变更。流程是：

1. 改 [`tools/docs-sync/docs-sync-rules.json`](../tools/docs-sync/docs-sync-rules.json)
2. 在**同一个 commit** 里改这份 md（保持人类版同步）
3. 在**同一个 commit** 里改 [`AGENTS.md` 的快速参考表](../AGENTS.md#quick-reference-the-8-current-rules)
4. 本地跑 `python3 tools/docs-sync/check_docs_sync.py --base origin/main` 自验
5. 写一个 `chore(docs-sync): …` commit 并在 message 里讲清动机

R0 自身的元规则保证这条规则也守同样的纪律。

## 规则维护者守则

写新规则时遵守这几条，避免规则膨胀到没人愿意维护：

- **每条规则都要有真实的失败案例**：能说出"如果不强制这条，过去某个 PR 就会让 X 文档变成谎话"才值得加。
- **优先选高信号路径**：`CheckDependencyRulesTask.kt` 改动 = 100% 需要更新 module-rules.md，这种规则才稳。模糊触发（"任何 Kotlin 改动都要同步文档"）会让校验器变成噪声源，被开发者全员 `[docs-skip]` 反向消解。
- **能写 `diff_must_contain` 就写**：比如 `settings.gradle.kts` 改了，但 diff 里没 `include(`，那大概率不是模块增减，不该报。这种"二阶过滤"能把误报率压到接近零。
- **目标是 8 条 ± 2**：再多就要考虑是不是工程已经发展到该拆分多个规则文件的阶段了。
