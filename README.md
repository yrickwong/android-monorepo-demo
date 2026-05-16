# Monorepo Demo（Android · Kotlin · Gradle KTS）

> 一个**极简但完整**的 Android Monorepo Demo，用来演示大型客户端代码库的工程治理能力：分层模块、Convention Plugin、依赖边界校验、依赖图生成、Affected modules 分析、CI。
>
> Demo 优先**可运行 + 结构清晰**，便于团队直接做 workshop / 演示。

## 1. 工程结构一览

```
app/                     :app                   组合根，注册路由
features/                :features:login/home/profile
bizlibs/                 :bizlibs:account/user
foundations/             :foundations:common/network/storage/router/analytics/ui
third-party/             :third-party:logger
build-logic/             Convention Plugins（独立 included build）
tools/affected-modules/  affected_modules.py
docs/                    架构文档 + 依赖图产物
.github/workflows/       CI
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
2. 默认填好的账密 `demo-user / demo-pass` 点击 Login。
3. 进入 Home，展示 `UserRepository` 返回的资料。
4. 点击 "Go to Profile" 进入 Profile 页。
5. 全程通过 `:foundations:analytics` 打 logcat（tag 前缀 `MonorepoDemo/`）。

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

## 7. CI

`.github/workflows/ci.yml` 在每个 PR / push 上执行：

1. `./gradlew checkDependencyRules`
2. `./gradlew generateDependencyGraph`
3. `./gradlew assembleDebug`

并把生成的依赖图作为 artifact 上传，方便在 PR 上预览。

## 8. 文档索引

- [`docs/architecture.md`](docs/architecture.md) — 整体架构、分层、业务流程、目录结构
- [`docs/module-rules.md`](docs/module-rules.md) — 依赖规则、矩阵、违规示例
- [`docs/dependency-graph.html`](docs/dependency-graph.html) — 依赖图（先跑 `generateDependencyGraph` 生成）
