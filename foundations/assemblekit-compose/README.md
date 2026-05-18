# AssembleKit · Compose flavour

> **一句话**：把 AssembleKit 的 `Page` 契约接到 Jetpack Compose 上，
> 让你能用 `@Composable Content()` 写一块 UI 切片，
> 但**生命周期、SavedState、scoped bus、Shell ViewModel、PageContext 这些规则
> 跟 [`:foundations:assemblekit`](../assemblekit) 完全一致** ——
> Compose 只是又一个渲染后端，不是另一个框架。

[`:foundations:assemblekit-compose`](.) 是 [`:foundations:assemblekit`](../assemblekit) 的**可选**伴生 module。
不写 Compose 的业务模块（例如所有走 XML 的老页面、以及任何 `:bizlibs:*`）**不要**依赖本模块，
就不必把 Compose 编译器、Compose BOM、`mavericks-compose` 一起拖进自己的构建图。

---

## 1. 为什么单独一个 module，而不是塞回 assemblekit？

| 关键约束 | 解释 |
| --- | --- |
| **不强加 Compose 工具链** | `:foundations:assemblekit` 是所有 feature / bizlib 都要用的底盘。塞进去等于强制全工程开 Compose 编译器，编译时间翻倍。 |
| **版本独立演进** | Compose 的 Kotlin / 编译器扩展版本耦合很紧（见 [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml) 的 `composeBom` / `composeCompiler` 注释）。隔离到独立 module 后，升 Compose ≠ 升 AssembleKit。 |
| **可见性边界友好** | `Page.materialize` 是 `protected abstract`（Kotlin 的 `internal` 跨 Gradle module 不能 override，[`Page.kt` 内有详细注释](../assemblekit/src/main/java/com/demo/foundations/assemblekit/Page.kt:140-150)）。一旦 ComposablePage 跟 ViewPage 不同 module，这个 `protected` 设计就是必须而不是可选的。 |
| **Convention plugin 单一职责** | 本模块走 [`demo.android.foundation.compose`](../../build-logic/convention/src/main/kotlin/com/demo/monorepo/buildlogic/AndroidFoundationComposeConventionPlugin.kt) plugin —— 把 Compose 的 buildFeature、编译器扩展、BOM 集中在一处声明。普通 foundation 还是用 [`demo.android.foundation`](../../build-logic/convention/src/main/kotlin/com/demo/monorepo/buildlogic/AndroidFoundationConventionPlugin.kt)。 |

依赖分层：本模块跟 [`:foundations:assemblekit`](../assemblekit) 一样属于 **FOUNDATION** 层
（`:foundations:*`），[`CheckDependencyRulesTask`](../../build-logic/convention/src/main/kotlin/com/demo/monorepo/buildlogic/CheckDependencyRulesTask.kt) 会自动按路径前缀识别，无需额外配置。

---

## 2. 模块结构

```text
foundations/assemblekit-compose/
├── build.gradle.kts                # plugin = demo.android.foundation.compose
└── src/main/
    ├── AndroidManifest.xml         # 空 manifest
    └── java/com/demo/foundations/assemblekit/compose/
        ├── ComposablePage.kt       # Page 的 Compose 子类型
        └── LocalPageContext.kt     # CompositionLocal<PageContext?> + composeConsume / composeRequireConsume
```

对外暴露的 API 面非常小，**故意如此**：
所有跟 MVI / 生命周期 / scope / bus 相关的 API 仍然由 [`:foundations:assemblekit`](../assemblekit) 提供，本模块**只补 Compose 的桥**。

---

## 3. 快速上手

### 3.1 模块依赖

只需要在某个 feature / bizlib 的 `build.gradle.kts` 里多一行：

```kotlin
dependencies {
    implementation(project(":foundations:assemblekit"))          // 仍然要
    implementation(project(":foundations:assemblekit-compose"))  // Compose 桥
}
```

Compose BOM、`compose.runtime` / `compose.ui` / `compose.foundation` / `mavericks-compose` 都通过
[`AndroidFoundationComposeConventionPlugin`](../../build-logic/convention/src/main/kotlin/com/demo/monorepo/buildlogic/AndroidFoundationComposeConventionPlugin.kt) 以 `api` 透出，业务侧不用再声明一次。

### 3.2 写一个 ComposablePage

```kotlin
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.demo.foundations.assemblekit.compose.ComposablePage
import com.demo.foundations.assemblekit.compose.composeRequireConsume

internal class FeedHeaderPage : ComposablePage() {

    @Composable
    override fun Content() {
        // 跟 ViewPage 里 `requireConsume(FeedShellViewModelKey)` 完全等价
        val shell = composeRequireConsume(FeedShellViewModelKey)
        val state by shell.collectAsState()
        Text(text = state.title)
    }
}
```

放进 `assemble {}` 跟 ViewPage 用法完全一样：

```kotlin
assemble(container = root) {
    provides(FeedShellViewModelKey, shellVm)
    +FeedHeaderPage()       // ComposablePage，Compose 渲染
    +FeedListPage(...)      // ListPage，RecyclerView 渲染
    +FeedFooterPage()       // ViewPage，XML 渲染
}
```

> Host 完全感知不到这是 Compose 还是 XML —— 这就是为什么"Page 是渲染机制无关"的契约值得维持。

### 3.3 在深层 Composable 里拿 Shell VM

跟 View 树里的 [`view.requirePageContext()`](../assemblekit/src/main/java/com/demo/foundations/assemblekit/ViewTreePageContext.kt) 同构。任何 `@Composable` 函数都可以：

```kotlin
@Composable
fun NoteActionBar(noteId: String) {
    val shell = composeRequireConsume(FeedShellViewModelKey)
    Button(onClick = { shell.like(noteId) }) { Text("Like") }
}
```

不需要从参数把 VM 一路传进去；不需要 Koin / Hilt。
关于"为什么不走 DI 容器"的完整论述见 [`docs/mvi-rules.md` § Why not Koin](../../docs/mvi-rules.md#why-not-koin)
和 [`:foundations:assemblekit` README § 8](../assemblekit/README.md#8-视图树pagecontextview-tree-pagecontext)。

---

## 4. 桥是怎么搭的（设计要点）

### 4.1 `materialize()` 返回 `ComposeView`

[`ComposablePage.materialize`](src/main/java/com/demo/foundations/assemblekit/compose/ComposablePage.kt) 做三件事：

1. **生成一个 `ComposeView`** 作为 Page 的根视图。框架后续会把它加进 assembly 容器、盖 View-tree `PageContext`。
2. **`ViewCompositionStrategy.DisposeOnLifecycleDestroyed(this)`** —— 注意 `this` 是 `ComposablePage` 本身（[`Page` 实现 `LifecycleOwner`](../assemblekit/src/main/java/com/demo/foundations/assemblekit/Page.kt:60)），而**不是** Host Activity。
   `Assembly.replace { ... }` 销毁旧 Page 时，对应的 composition 会**立刻**释放，
   即便 Host 还在 RESUMED。
3. **把 `PageContext` 注入 Composition** —— `CompositionLocalProvider(LocalPageContext provides pageContext) { Content() }`。
   `Content()` 是 `@Composable protected abstract`，子类按 Compose 习惯写就行。

### 4.2 `LocalPageContext`：CompositionLocal 版的 View-tree PageContext

[`LocalPageContext`](src/main/java/com/demo/foundations/assemblekit/compose/LocalPageContext.kt) 用 `compositionLocalOf { null }` —— 默认 `null`，
不会在被 Preview / Paparazzi 单独渲染时抛错。

| 函数 | 行为 |
| --- | --- |
| `LocalPageContext.current` | 当前 composition 的 PageContext，可为 `null` |
| `requirePageContext()` | 同上但 `null` 抛 `error(...)`，错误信息里指明可能是 Preview 没注入 |
| `composeConsume(key)` | 等价于 [`Page.consume(key)`](../assemblekit/src/main/java/com/demo/foundations/assemblekit/Page.kt:195)，从 page → assembly → host 链上找，找不到返回 `null` |
| `composeRequireConsume(key)` | 等价于 [`Page.requireConsume(key)`](../assemblekit/src/main/java/com/demo/foundations/assemblekit/Page.kt:198)，找不到抛错 |

### 4.3 Mavericks 集成

本模块**不重新实现** `mavericksViewModel` —— 直接通过 `api(libs.mavericks.compose)` 把 Airbnb 官方
[`mavericks-compose`](https://github.com/airbnb/mavericks/blob/main/mvrx-compose/) 透出。
你在 `Content()` 里可以直接 `import com.airbnb.mvrx.compose.mavericksActivityViewModel` 用它的 helper：

```kotlin
@Composable
override fun Content() {
    val vm: FeedHeaderViewModel = mavericksActivityViewModel()
    val state by vm.collectAsState()
    // ...
}
```

`pageViewModel()` 委托（per-Page VM）跟 View 世界完全一样工作（因为它本身就是 View 无关的），
所以 `private val vm by pageViewModel<MyVm, MyState>()` 跟 `Content()` 是兼容的。

---

## 5. 反模式（CR 会拦的写法）

| 反模式 | 应该怎么写 | 为什么 |
| --- | --- | --- |
| `class MyPage : ComposablePage() { override fun materialize(...) = MyComposeView(...) }` 自己 new `ComposeView` | 只重写 `Content()`，让基类的 `materialize` 做事 | 自定义 `ComposeView` 就跳过了 `DisposeOnLifecycleDestroyed` + `LocalPageContext` 注入，视图找不到 PageContext，深层 widget 直接挂 |
| `@Composable fun X() { val ctx = LocalLifecycleOwner.current; ... }` 用 LocalLifecycleOwner 当 Page lifecycle | 用基类继承的 `lifecycle` 属性 | `LocalLifecycleOwner.current` 解析到的是 Activity，跟 Page lifecycle 错位，Page 已 detach 但 lifecycle 还 RESUMED 会写出泄漏 |
| `private val shell = KoinJavaComponent.get<FeedShellViewModel>()` 字段拿 VM | `composeRequireConsume(FeedShellViewModelKey)` | 跟 View 世界一样的"DI 后门"问题，多 host 拿错 VM，详见 [`docs/mvi-rules.md` § Why not Koin](../../docs/mvi-rules.md#why-not-koin) |
| 在 `Content()` 之外做 `setContent { ... }` | 全部 UI 写在 `Content()` 里 | 框架的 disposal / context 注入只对 `Content()` 这一棵 composition 生效，自己另起一棵 == 自己手动管理生命周期 == 一定漏 |
| 把 `Content()` 暴露成 `public` 给外部调用 | 它就是 `protected abstract`，外部要复用就抽公共 `@Composable` 函数 | `Content()` 依赖 `LocalPageContext` 已被注入，脱离 ComposablePage 直接调一定会 `error(...)` |

---

## 6. 测试 / Preview

### 6.1 单测 ComposablePage

测试时不需要起 Activity，可以直接构造 Page + 假 PageContext，跟 ViewPage 测试套路一样
（参考 [`:foundations:assemblekit` README § 13 FAQ](../assemblekit/README.md#13-faq)）。
Compose UI 行为用 `androidx.compose.ui.test`，跟普通 Compose 项目无差异。

### 6.2 Preview 想引用 `composeRequireConsume`

Preview 没有 ComposablePage 包着，`LocalPageContext.current` 默认是 `null`，
直接调 `composeRequireConsume` 会抛错。Preview 里用 `CompositionLocalProvider` 注入一个 fake：

```kotlin
@Preview
@Composable
private fun PreviewNoteActionBar() {
    CompositionLocalProvider(LocalPageContext provides fakePageContext()) {
        NoteActionBar(noteId = "demo")
    }
}
```

`fakePageContext()` 由你自己在测试源集里实现 —— 给 Shell VM 喂一个静态 state 就行，
不需要真的拉 [`:foundations:assemblekit`](../assemblekit) 的运行时。

---

## 7. 还没做但已经规划的

- **Compose 版的 `MultiTypeListPage`** —— 当前 [`MultiTypeListPage`](../assemblekit/src/main/java/com/demo/foundations/assemblekit/list/MultiTypeListPage.kt) 走 RecyclerView，列表里既要混 ComposablePage 又要混 XML 行的场景目前还是用 RecyclerView 包；如果未来出现"整屏 Compose feed"的真实业务，会补 `ComposableListPage<T>` 走 `LazyColumn`。
- **Hot-reload / Preview 联动** —— 等 Compose Live Edit 在我们用的 AGP / IntelliJ 版本上稳定后再统一接。
- **shared-element transitions across ComposablePage ↔ ViewPage** —— 跨渲染机制做共享元素动画暂无业务用例，写之前先量价值。

如果你正在做一个会用到上面任何一项的 feature，请在 [`docs/architecture.md`](../../docs/architecture.md) 提一笔，
让我们一起决定"加进来"还是"用本 feature 内的私有方案先扛过去"。

---

## 8. 延伸阅读

| 想看 | 看这里 |
| --- | --- |
| AssembleKit 总览（一定先读这个） | [`:foundations:assemblekit/README.md`](../assemblekit/README.md) |
| MVI 6 条不可商量规则 + 反模式 | [`docs/mvi-rules.md`](../../docs/mvi-rules.md) |
| 框架在工程里的整体位置 | [`docs/architecture.md`](../../docs/architecture.md) |
| 依赖分层规则（本模块属 FOUNDATION 层） | [`docs/module-rules.md`](../../docs/module-rules.md) |
| 编码规范 / docs-sync 强约束 | [`AGENTS.md`](../../AGENTS.md) |
