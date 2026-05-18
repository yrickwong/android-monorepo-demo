# 模块依赖规则

> 校验由 `./gradlew checkDependencyRules` 强制执行（实现：`build-logic/convention/src/main/kotlin/com/demo/monorepo/buildlogic/CheckDependencyRulesTask.kt`）。违反任意一条规则都会让构建失败。
>
> 这是工程的"分层契约"。它与 [`AGENTS.md` § Rule 1](../AGENTS.md#rule-1-layered-module-boundaries) 同源；修改本文件的同时通常也要修改 `CheckDependencyRulesTask.kt`，反之亦然（[docs-sync R2](doc-sync-rules.md#r2--dependency-rule-task-changes-must-update-module-rulesmd)）。

## 规则总览

| 来源层 | 禁止依赖 | 原因 |
| --- | --- | --- |
| `:features:*` | 其他 `:features:*` | feature 必须独立可拆解；跨 feature 跳转用 `:foundations:router` |
| `:bizlibs:*`  | `:features:*` | 业务库面向多个 feature 复用，不能反向耦合任何具体 UI |
| `:foundations:*` | `:features:*` / `:bizlibs:*` | foundation 是平台能力，不应了解任何业务 |
| `:third-party:*` | `:app` / `:features:*` / `:bizlibs:*` / `:foundations:*` | 三方层只承担适配，禁止往业务方向反向依赖 |

`:app` 是唯一能依赖所有层的模块——它是组合根（composition root）。

## 各层允许依赖矩阵

下表中 ✅ 表示允许，❌ 表示禁止：

| FROM \ TO | `:app` | `:features:*` | `:bizlibs:*` | `:foundations:*` | `:third-party:*` |
| --- | --- | --- | --- | --- | --- |
| `:app` | — | ✅ | ✅ | ✅ | ✅ |
| `:features:*` | ❌ | ❌ | ✅ | ✅ | ✅ |
| `:bizlibs:*` | ❌ | ❌ | ✅ | ✅ | ✅ |
| `:foundations:*` | ❌ | ❌ | ❌ | ✅ | ✅ |
| `:third-party:*` | ❌ | ❌ | ❌ | ❌ | ✅ |

## 为什么这样划分

- **`features` 横向解耦**：跨 feature 的跳转走 `:foundations:router`。在 demo 中，`HomeActivity` 想打开 `ProfileActivity` 时调用 `Router.navigate(this, Router.Paths.PROFILE)`，而不是 `import com.demo.features.profile.ProfileActivity`。这样 `:features:home` 和 `:features:profile` 才能保持相互独立、可独立维护、可被裁剪。
- **`bizlibs` 抽业务**：feature 之间复用的业务能力（账号、用户、IM 等）下沉到 bizlib。bizlib 持有数据与状态，feature 只做 UI 编排。
- **`foundations` 做平台**：网络、存储、路由、埋点是稳定的平台能力，独立于业务。页面装配（`:foundations:assemblekit`）也归在这一层——它是平台级 UI 装配能力，谁都不允许反向依赖它。
- **`third-party` 做适配**：第三方 SDK / 适配器，禁止反向依赖业务，避免“升级一个 SDK 拉爆整个项目”。

> 同层互依赖在 `:foundations:*` 内部是被允许的（例如 `:foundations:assemblekit → :foundations:common`、`:foundations:ui → :foundations:common`），这是为了让 foundation 可以复用更小的 foundation 原子；其他层一律禁止同层互依赖。

## 触发非法依赖示例

下面是几个**会被 `checkDependencyRules` 抓出来**的写法，用于自测和演示。

### 示例 A：feature 之间互相依赖（禁止）

在 `features/home/build.gradle.kts` 中加入：

```kotlin
dependencies {
    // 故意违规
    implementation(project(":features:profile"))
}
```

执行：

```bash
./gradlew checkDependencyRules
```

将得到类似输出：

```
[checkDependencyRules] FAILED — illegal dependencies detected:

  1. :features:home  →  :features:profile   [features must not depend on other features]

See docs/module-rules.md for the layering rules.
```

### 示例 B：foundations 依赖 bizlib（禁止）

在 `foundations/network/build.gradle.kts` 中加入：

```kotlin
dependencies {
    implementation(project(":bizlibs:account")) // ❌
}
```

执行任务后将看到：

```
1. :foundations:network  →  :bizlibs:account   [foundations must not depend on bizlibs]
```

### 示例 C：bizlib 依赖 feature（禁止）

在 `bizlibs/user/build.gradle.kts` 中：

```kotlin
dependencies {
    implementation(project(":features:home")) // ❌
}
```

输出：

```
1. :bizlibs:user  →  :features:home   [bizlibs must not depend on features]
```

### 示例 D：third-party 反向依赖业务（禁止）

在 `third-party/logger/build.gradle.kts` 中：

```kotlin
dependencies {
    implementation(project(":foundations:analytics")) // ❌
}
```

输出：

```
1. :third-party:logger  →  :foundations:analytics   [third-party must not depend on business modules]
```

## 合法依赖示例（不会被校验拦下）

| 边 | 解释 |
| --- | --- |
| `:features:login → :foundations:assemblekit` | feature → foundation，规则允许 |
| `:features:feed  → :foundations:assemblekit` | 同上：feed 作为 AssembleKit v2 综合 demo，依赖框架核心 |
| `:foundations:assemblekit → :foundations:common` | foundation 内部互相依赖，允许 |
| `:foundations:assemblekit → :third-party:logger`  | foundation → third-party，允许 |
| `:foundations:assemblekit → androidx.recyclerview` | 第三方库，外部依赖（不在分层校验范围） |
| `:app → :foundations:assemblekit` | app 是组合根，可以依赖任何模块 |
| `:app → :features:feed` | app 是唯一允许依赖 `:features:*` 的模块 |

> `:features:home` 上加 `Open Feed` 按钮时**不需要**依赖 `:features:feed`——通过 `Router.navigate(this, Router.Paths.FEED)` 跳转，跨 feature 跳转一律走路由层，这是 `checkDependencyRules` 仍然 OK 的关键。

新增 foundation 模块时，按上面这几条对照即可——既不需要改 `CheckDependencyRulesTask`，也不需要在这份文档里追加新规则。

## 实现细节

- 只检查**当前模块声明的**依赖，不解析 transitive；避免误把 AndroidX 内部依赖判成违规。
- 校验的配置范围：`api`、`implementation`、`compileOnly`、`runtimeOnly`、`debug/releaseImplementation`、以及 `test*Implementation`。
- 任何不属于五层的模块（理论上不应存在）会被静默忽略，方便未来扩展。
