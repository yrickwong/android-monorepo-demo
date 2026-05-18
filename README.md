# Monorepo Demo（Android · Kotlin · Gradle KTS）

> 一个**极简但完整**的 Android Monorepo Demo，用来演示大型客户端代码库的工程治理能力：分层模块、Convention Plugin、依赖边界校验、依赖图生成、Affected modules 分析、CI，以及一套 **DSL 化的页面装配框架（AssembleKit + Mavericks MVI）**。
>
> Demo 优先**可运行 + 结构清晰**，便于团队直接做 workshop / 演示。

## 1. 工程结构一览

```
AGENTS.md                Agent / 人类 工作规则（R0 强制：代码改动 → 文档同步）
app/                     :app                   组合根，注册路由
features/                :features:login/home/profile/feed
bizlibs/                 :bizlibs:account/user
foundations/             :foundations:common/network/storage/router/analytics/ui
                         :foundations:communicate  (SPI: feature/bizlib ↔ app)
                         :foundations:assemblekit  (Page / Assembly DSL + Mavericks MVI)
                                                   v2: ListPage + scoped locals + at(id) + replace
third-party/             :third-party:logger
build-logic/             Convention Plugins（独立 included build）
tools/affected-modules/  affected_modules.py        增量构建影响范围分析
tools/docs-sync/         check_docs_sync.py         代码 → 文档 同步校验（R0）
docs/                    架构文档 + 依赖图产物 + doc-sync-rules.md
.github/workflows/       CI（含 docs sync check + dep rules + build）
```

每个模块只在自己的 `build.gradle.kts` 里写**两件事**：

1. `id("demo.android.*")` 选 convention plugin。
2. `namespace` + 业务依赖。

`compileSdk` / `minSdk` / Kotlin / 单元测试配置等**完全不出现在模块脚本里**，统一由 `build-logic/convention` 注入。

## 2. 业务流程

```
Launcher → Login → Home → Profile
            │       │       │
        Account  User    User
          Repo   Repo    Repo
            │       │       │
        Network/Storage/Analytics (foundations)
                    │
                Logger (third-party)
```

跳转通过 `:foundations:router` 完成，因此 `:features:*` 之间没有直接依赖。

## 3. 如何运行 Demo

### 3.1 准备

- JDK 17（Convention Plugin 用 17 编译，Android Gradle Plugin 8.2 也要求 17+）
- Android SDK，`compileSdk=34`，`minSdk=23`
- 第一次需要**生成 Gradle Wrapper**（仓库未携带 `gradle-wrapper.jar`）：
  ```bash
  cd monorepo-demo
  gradle wrapper --gradle-version 8.5
  ```
  之后即可使用 `./gradlew`。

### 3.2 构建 / 安装

```bash
./gradlew :app:assembleDebug         # 构建 APK
./gradlew :app:installDebug          # 安装到连接的设备
```

启动后：
1. App 进入 `LauncherActivity`，由 `:foundations:router` 跳到 Login。
2. Login 页由 `assemble { +LoginHeaderPage(); +LoginBodyPage(); +LoginBottomPage() }` 装配出来，Body 内部用 Mavericks `Async<Session>` 表达登录过程，三个 Page 之间靠 `ScopedEventBus` 通信。
3. 默认填好的账密 `demo-user / demo-pass` 点击 Login。
4. 进入 Home，展示 `UserRepository` 返回的资料。
5. 点击 "Go to Profile" 进入 Profile 页。
6. 点击 "Open Feed (AssembleKit v2 demo)" 进入 `:features:feed`——它一口气演示了 v2 的四件套：
   - `ListPage<Note>` + `ItemBinder` 渲染 RecyclerView 列表，行的点击/数据全部通过 `consume(...)` 获取（context transparency）；
   - `provides(FeedRepositoryKey, repo)` 把仓库挂到 assembly 作用域，列表行和 footer 各自独立拿；
   - 三个 Page 用 `+HeaderPage() at R.id.feed_header_slot` 等 `at(id)` 语法挂到多槽位布局；
   - 头部的"Toggle banner"按钮触发 `assembly.replace { … }`，由宿主整体重组当前 Assembly（增删一个 `FeedBannerPage`），其他 Page 状态不丢。
7. 全程通过 `:foundations:analytics` 打 logcat（tag 前缀 `MonorepoDemo/`）。

> 想看页面装配框架的设计动机、三层 scope 模型与替代时机，见 [`docs/architecture.md` § 页面装配框架（AssembleKit）](docs/architecture.md#页面装配框架assemblekit)；v2 的 locals / mount / replace / list / Compose roadmap 细节见同节 [§ AssembleKit v2](docs/architecture.md#assemblekit-v2列表上下文多槽位host-驱动-replace)。

## 4. 如何触发非法依赖检查

```bash
./gradlew checkDependencyRules
```

正常情况下输出：

```
[checkDependencyRules] OK — all module dependencies are legal.
```

### 故意制造一条违规来演示

在 `features/home/build.gradle.kts` 的 `dependencies { ... }` 里加：

```kotlin
implementation(project(":features:profile"))
```

再次执行：

```
[checkDependencyRules] FAILED — illegal dependencies detected:

  1. :features:home  →  :features:profile   [features must not depend on other features]

See docs/module-rules.md for the layering rules.
```

详细规则与更多反例见 [`docs/module-rules.md`](docs/module-rules.md)。

## 5. 如何生成依赖图

```bash
./gradlew generateDependencyGraph
```

产物（写在 `docs/`）：

| 文件 | 用途 |
| --- | --- |
| `docs/dependency-graph.json` | 机器可读，供 affected modules 分析使用 |
| `docs/dependency-graph.dot`  | Graphviz；`dot -Tpng docs/dependency-graph.dot -o graph.png` |
| `docs/dependency-graph.html` | 浏览器直接打开，含表格 + 边列表，按层着色 |

## 6. 如何运行 Affected Modules 分析

依赖：Python 3.8+。

```bash
# 先确保有最新的依赖图
./gradlew generateDependencyGraph

# 分析当前分支相对 main 的变更影响范围
python3 tools/affected-modules/affected_modules.py --base main --pretty
```

示例输出：

```json
{
  "base": "main",
  "changedFiles": ["foundations/network/src/main/java/com/demo/foundations/network/HttpClient.kt"],
  "unmappedFiles": [],
  "changedModules": [":foundations:network"],
  "affectedModules": [
    ":app",
    ":bizlibs:account",
    ":bizlibs:user",
    ":features:feed",
    ":features:home",
    ":features:login",
    ":features:profile",
    ":foundations:network"
  ],
  "suggestedGradleTasks": [
    ":app:assembleDebug",
    ":app:testDebugUnitTest",
    ...
  ]
}
```

CI 可以用 `jq -r '.suggestedGradleTasks[]'` 拼成增量 `./gradlew` 命令。

## 7. 文档同步规则（R0）

> 简短版：**改了代码，对应的 md 必须在同一个 commit / PR 里跟着改**。CI 强制。

完整的"改 X → 必须同步 Y"映射表在 [`AGENTS.md` § Rule 0](AGENTS.md#rule-0-docs-sync-contract-mandatory)，人类详述版在 [`docs/doc-sync-rules.md`](docs/doc-sync-rules.md)，机器可读的真源是 [`tools/docs-sync/docs-sync-rules.json`](tools/docs-sync/docs-sync-rules.json)。

本地自查：

```bash
python3 tools/docs-sync/check_docs_sync.py --base origin/main
```

可选：装一个本地 post-commit 提示钩子（仅 warn，不阻断）：

```bash
bash tools/docs-sync/install-hooks.sh
```

逃生舱：在任一 commit message 里写 `[docs-skip]` 或 `[docs-skip:R3-new-module]`，并在 PR 描述里写清理由。

## 8. CI

`.github/workflows/ci.yml` 在每个 PR / push 上执行：

1. `python3 tools/docs-sync/check_docs_sync.py --strict` — 文档同步契约（R0）
2. `./gradlew checkDependencyRules` — 分层依赖校验
3. `./gradlew generateDependencyGraph` — 重新生成依赖图
4. `./gradlew assembleDebug` — 全量 debug 构建

依赖图与 APK 都作为 artifact 上传，方便在 PR 上预览。文档同步在最前面，目的是"文档不对就别浪费 CI 机器跑构建"。

## 9. 文档索引

- [`AGENTS.md`](AGENTS.md) — Agent / 人类工作规则（R0 文档同步契约就在这里）
- [`docs/architecture.md`](docs/architecture.md) — 整体架构、分层、业务流程、AssembleKit v2
- [`docs/module-rules.md`](docs/module-rules.md) — 依赖规则、矩阵、违规示例
- [`docs/doc-sync-rules.md`](docs/doc-sync-rules.md) — 文档同步规则人类详述版
- [`docs/dependency-graph.html`](docs/dependency-graph.html) — 依赖图（先跑 `generateDependencyGraph` 生成）
