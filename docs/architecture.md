# 架构总览

本工程是一个 **Android Monorepo Demo**，用于演示大型客户端代码库的工程治理能力。它在尽量小的代码量下覆盖了以下能力：

1. 严格的分层模块结构
2. Gradle Convention Plugins（避免模块重复配置）
3. 依赖边界自动校验
4. 依赖图自动生成（json / dot / html）
5. Affected modules 分析（用于 CI 增量构建）

## 模块分层

按职责自上而下分为五层。**只能向下依赖**，绝不允许向上或同层互调（同层互调只在 `:foundations:*` 之间被允许）。

```
+-----------------------------------------------------------------+
|  :app                       (composition root, 应用壳)          |
+-----------------------------------------------------------------+
|  :features:login   :features:home   :features:profile           |
|  :features:feed    :features:mainframe                          |
+-----------------------------------------------------------------+
|  :bizlibs:account   :bizlibs:user                               |
+-----------------------------------------------------------------+
|  :foundations:common   :foundations:network   :foundations:storage|
|  :foundations:router   :foundations:analytics :foundations:ui    |
|  :foundations:communicate (SPI: feature/bizlib ↔ app 反向通信)    |
|  :foundations:assemblekit (Page / Assembly DSL · Mavericks MVI)   |
|  :foundations:assemblekit-compose (可选 · Compose 渲染桥 · 见 §AssembleKit v2.2)|
|  :foundations:slidepane (水平三槽位滑动容器 · 与 assemblekit 正交)  |
+-----------------------------------------------------------------+
|  :third-party:logger                                            |
+-----------------------------------------------------------------+
```

| 层级 | 职责 | 允许依赖 |
| --- | --- | --- |
| `:app` | 应用入口、路由注册、组合所有 feature | 所有层 |
| `:features:*` | 单个端到端业务页面（Activity/ViewModel/Layout） | `:bizlibs:*` / `:foundations:*` / `:third-party:*` |
| `:bizlibs:*` | 跨 feature 的业务能力（账号、用户资料……） | `:foundations:*` / `:third-party:*` / 其他 `:bizlibs:*` |
| `:foundations:*` | 与业务无关的平台能力（网络、存储、路由、埋点、UI 公共组件、**页面装配**） | `:foundations:*` / `:third-party:*` |
| `:third-party:*` | 三方 / 适配层 | 任何业务模块都禁止依赖 |

详细的边界规则与失败示例见 [`module-rules.md`](module-rules.md)。

## 业务流程

最小演示流程串起所有关键模块：

```
LauncherActivity (:app)
        │
        ▼  Router.navigate(LOGIN)
LoginActivity (:features:login)
        │   ├── 由 assemble {} 装配 Header / Body / Bottom 三个 Page
        │   │      ↑ :foundations:assemblekit + Mavericks MVI
        │   ├── AccountRepository.login() ← :bizlibs:account → :foundations:network/storage
        │   └── Analytics.logEvent()      ← :foundations:analytics
        ▼  Router.navigate(HOME)
HomeActivity (:features:home)
        │   UserRepository.loadCurrentUser()  ← :bizlibs:user
        ├── Router.navigate(PROFILE) ─▶ ProfileActivity (:features:profile)
        ├── Router.navigate(FEED)    ─▶ FeedActivity    (:features:feed)         AssembleKit v2 综合 demo
        └── Router.navigate(MAINFRAME)─▶ MainActivity   (:features:mainframe)    SlidePane + AssembleKit 三屏滑动主框架 demo
```

> **要点：** Login / Home / Profile / Feed / Mainframe 之间的跳转**不**通过相互依赖，而是通过 `:foundations:router` 实现的字符串路由（`Router.Paths` 是跨 feature 导航的唯一契约）。这让“`:features:*` 不能依赖其他 `:features:*`”这条规则在编译期和 `checkDependencyRules` 任务里都能被守住。新增一个 feature 入口的标准动作就是：在 `Router.Paths` 加 `const val`，在 `:app` 的 `DemoApp.onCreate` 里 `Router.register(...)` 一次，调用方写 `Router.navigate(this, Router.Paths.X)`。

## 工程治理能力

### 1. Convention Plugins

`build-logic/convention` 中实现了 6 个 plugin：

| Plugin id | 用途 |
| --- | --- |
| `demo.android.application` | `:app` 唯一使用，配置 application、versionCode/Name |
| `demo.android.feature` | `:features:*` 使用，开启 viewBinding |
| `demo.android.bizlib` | `:bizlibs:*` 使用 |
| `demo.android.foundation` | `:foundations:*` 使用（XML/View 工具链，零 Compose 成本） |
| `demo.android.foundation.compose` | `:foundations:*` 中**需要 Compose** 的模块使用——在 `foundation` 之上叠加 `buildFeatures.compose`、固定 `composeCompiler` 扩展版本（与 `libs.versions.toml` 中 `composeCompiler` / Kotlin 版本三方对齐）、并以 `api` 透出 Compose BOM + `runtime` / `ui` / `foundation` / `ui-tooling-preview`；今天唯一消费者是 [`:foundations:assemblekit-compose`](../foundations/assemblekit-compose/README.md) |
| `demo.kotlin.library` | 纯 Kotlin/JVM 模块（如 `:third-party:logger`） |
| `demo.dependency.guard` | 注册根任务：`checkDependencyRules` / `generateDependencyGraph` |

> 大多数 foundation 模块都沿用 `demo.android.foundation`，无需新 plugin。`:foundations:assemblekit-compose` 是目前唯一例外——它走 `demo.android.foundation.compose` 以便集中管理 Compose 编译器扩展版本与 BOM；如果将来再出现"必须用 Compose 的 foundation"（例如一套 Compose 版的设计系统 shell），优先复用这个 plugin 而不是在自己的 `build.gradle.kts` 里就地启用 Compose。

所有 `compileSdk`、`minSdk`、Kotlin options、JVM target、test runner、common dependencies 都集中在 `AndroidCommon.kt` 中，模块只声明自己的 `namespace` 和**业务依赖**。

### 跨层反向通信（SPI）

`:foundations:communicate` 解决一个分层 monorepo 里绕不开的问题——**下层模块需要使用上层（通常是 `:app`）才能提供的能力**（读 `BuildConfig`、调起全局退出登录、拿宿主主题色……）。直接 `feature → app` 被 `checkDependencyRules` 禁止，因此走 SPI：

1. **接口下沉**：在 `:foundations:communicate` 声明 `interface IXxx`，下层全都能看到。
2. **实现上提**：在 `:app`（composition root）写 `class XxxImpl : IXxx`，并在 `Application.onCreate` 里 `ServiceRegistry.register<IXxx>(XxxImpl(this))`（或 `registerLazy` 推迟实例化）。
3. **运行期解析**：任意 `:features:*` / `:bizlibs:*` 通过 `ServiceRegistry.get<IXxx>()` 拿到实现，**不需要 import 任何 `:app` 包**。

依赖方向（编译期）依旧只朝下：`:app → :foundations:communicate ← :features:*`。
实现方向（运行期）则"向上回流"：`:app` 把实现注入 registry，feature 拉取。这就达成了**编译期解耦 + 运行期组合**。

#### Demo 中内置的 3 个 SPI

| 接口 | 类型 | 注册方式 | App 侧实现 | 消费侧 |
| --- | --- | --- | --- | --- |
| `IAppEnv` | 读：App 环境信息（version / debuggable / channel） | `register` (eager) | `AppEnvImpl` (`:app`) | `HomeActivity` 上报到埋点 |
| `IRemoteConfig` | 读：运行期开关 / 字符串 / 数字 | `registerLazy`（首次 `get` 才创建） | `RemoteConfigImpl` (`:app`) | `HomeActivity` 控制按钮显示；`LoginActivity` 控制账号预填 |
| `ILogoutService` | 写/动作：清会话 + 路由回登录 + 提示 | `register` (eager) | `LogoutServiceImpl` (`:app`，编排 `:bizlibs:account` + `:foundations:router` + `:foundations:ui`) | `HomeActivity` 的 Logout 按钮 |

#### 调用样例

注册侧（[`DemoApp`](../app/src/main/java/com/demo/monorepo/app/DemoApp.kt)）：

```kotlin
ServiceRegistry.register<IAppEnv>(AppEnvImpl(this))
ServiceRegistry.register<ILogoutService>(LogoutServiceImpl())
ServiceRegistry.registerLazy<IRemoteConfig> { RemoteConfigImpl() }   // 首次 get 时构造
```

消费侧（[`HomeActivity`](../features/home/src/main/java/com/demo/features/home/HomeActivity.kt)）：

```kotlin
// 读：远端配置
val showProfileButton = ServiceRegistry.get<IRemoteConfig>()
    .getBoolean(IRemoteConfig.Keys.HOME_SHOW_PROFILE_BUTTON, default = true)
binding.goProfile.visibility = if (showProfileButton) View.VISIBLE else View.GONE

// 写：触发全局登出
binding.logout.setOnClickListener {
    ServiceRegistry.get<ILogoutService>().logout(this, reason = "user_home")
}
```

#### 如何加一个新的 SPI

1. 在 `:foundations:communicate` 加 `interface IXxx` —— 一定要**与具体 UI / 业务无关**。
2. 在能"看得到所有依赖"的模块（通常是 `:app`，少数情况下是 `:bizlibs:*`）写实现类。
3. 在 `Application.onCreate` 里 `ServiceRegistry.register<IXxx>(...)`。
   - 初始化开销大、可能用不到的服务建议改用 `registerLazy { ... }`。
4. 任意 feature/bizlib 加 `implementation(project(":foundations:communicate"))` 即可消费，**不需要**改 dependency-rules。

> 反模式提醒：不要把 SPI 接口塞进 `:bizlibs:*` 或具体 feature，那会让其他 feature 为了拿一个接口被迫依赖一个不相关的业务库；接口的"中立性"才是 SPI 解耦的关键。

### 页面装配框架（AssembleKit）

`:foundations:assemblekit` 解决另一类问题——**单个页面在功能堆叠后，Activity/Fragment 容易膨胀成上千行的"上帝类"**。常见症状：

- 一个 `LoginActivity` 同时管文案、输入校验、提交、loading、埋点、路由；想做 AB 测试要在中间加 `if`。
- 想把"输入区"复用到另一个页面，得把整段代码连同私有字段拷过去。
- ViewModel 是页面级单例，单元测试得一次性把所有状态都准备好。

assemblekit 的处方：

1. **把页面切成多个 Page**：每个 Page 是一段独立 UI 切片，拥有自己的生命周期、`SavedStateRegistry`、（可选的）Mavericks `ViewModel`。`Page` 实现 `LifecycleOwner / SavedStateRegistryOwner / MavericksView`，但不是 Fragment——不进回退栈，不走 FragmentManager，可以直接 `new` 出来做单测。
2. **用 DSL 声明组合**：宿主里只写一段 `assemble { +HeaderPage(); +BodyPage(); +BottomPage() }`，删/换/AB 一个 Page 是一行代码的事。
3. **三层作用域 + 总线隔离事件**：每个 Page 看得到三个 scope 与对应的 `ScopedEventBus`：
   - `pageScope` / `pageBus` —— 自己内部
   - `assemblyScope` / `assemblyBus` —— 同一 Assembly 的兄弟 Page
   - `hostScope` / `hostBus` —— 整个宿主（Activity / Fragment）

   Page 发什么、听什么，全靠类型化事件（sealed class）；从来不直接持有兄弟 Page 的引用。
4. **Mavericks MVI 兜底状态**：状态是 immutable 的 `MavericksState`，副作用以 `Async<T>` 表达。`pageViewModel()` 把 VM 存到宿主 Activity 的 `ViewModelStore`，key 用 `{pageId}::{VMClass}`，旋屏后自动恢复。

#### Demo：`:features:login` 怎么被装配出来

```kotlin
// LoginActivity.kt —— 整个 Activity 只剩下三件事
class LoginActivity : PageHostActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LoginActivityBinding.inflate(layoutInflater).also { setContentView(it.root) }

        assemble(container = binding.assemblyContainer) {
            +LoginHeaderPage()    // 静态文案，无 VM
            +LoginBodyPage()      // Mavericks VM + 输入校验 + 提交
            +LoginBottomPage()    // 按钮 + loading，无 VM
        }

        // 宿主只关心 LoginFinished —— 由谁怎么走到这一步它不在乎
        hostBus.on<LoginEvent.LoginFinished>(lifecycleScope) { event ->
            if (event.success) { Router.navigate(this, Router.Paths.HOME); finish() }
            else Toaster.short(this, "Login failed: ${event.errorMessage}")
        }
    }
}
```

事件流：
- `Body` 监听输入 → 向 **assemblyBus** 广播 `CredentialsChanged`
- `Bottom` 收到 `CredentialsChanged` 控制按钮 enabled；点击 → 向 **assemblyBus** 广播 `SubmitClicked`
- `Body` 收到 `SubmitClicked` → 调 `viewModel.submit()`（包装 `AccountRepository.login` 为 `Async`）
- `Body` 把最终结果以 `LoginFinished` 发到 **hostBus**，宿主 Activity 决定路由

整条链路里 `LoginActivity` 不知道有 `username`/`password` 字段，三个 Page 之间也互不持有引用。删除 `LoginBottomPage` 整套登录流程仍然可用（只是没按钮可点），换成 `LoginBiometricPage` 也不影响其他两个 Page。

#### 何时该上 assemblekit？何时不该？

| 场景 | 推荐方案 |
| --- | --- |
| 单一职责的轻页面（< 200 行） | 普通 Activity / Fragment，**不要**为了用 DSL 而拆 |
| 多模块协同 / AB 频繁 / 多块独立状态 | assemblekit |
| 跨页面共享一份数据 | 业务下沉到 `:bizlibs:*`，Page 只消费 |
| 跨进程 / 跨 Activity 的事件 | 走 `:foundations:communicate` SPI，不要拿 hostBus 凑数 |

> 与 SPI 的分工：**SPI 解耦"上下层之间不能直接 import"**，assemblekit 解耦**"同一层、同一页面内部模块之间的引用"**。两者正交，互不替代。

#### AssembleKit v2：列表、上下文、多槽位、Host 驱动 replace

v2 把上面的"一个页面装三个 Page"扩展到**真实业务页面常见的四种增量需求**——全部以**最小可读**为目标，不引入新概念栈。

> **硬约束（写业务前必读）**：所有业务页面**必须**走 `assemble {}` + Mavericks。
> 任何"页面里有可观察状态"的代码——网络回包、用户输入、loading/失败、列表数据——
> 一律以 `MavericksState` + `MavericksViewModel`（或 `PageViewModel`）持有，Pages 通过
> `viewModel.onEach(prop)` / `onAsync(prop)` 订阅切片，**严禁**在 Page 里直接 `collect`
> 一个 `Repository.someFlow` 或者自己持有 `MutableStateFlow`。规则全文与反模式清单见
> [`docs/mvi-rules.md`](mvi-rules.md)，强制性条款写在 [`AGENTS.md`](../AGENTS.md) Rule 2。
> 这条约束的存在是为了让"两套真相源 (repo flow + Mavericks state) 漂移"
> 这种最常见的 bug 变成一条**结构性不可能发生**的属性，而不是靠 CR 抓。

| 主题 | 入口 API | 解决了什么问题 |
| --- | --- | --- |
| Page 抽象分层 | `Page` (base) / `ViewPage` / `AsyncViewPage` / `ComposablePage` (in `:foundations:assemblekit-compose`) | 让框架核心和 UI 渲染机制解耦——XML（同步或异步 inflate）和 Compose 是平级的渲染后端，`Assembly` / `PageContext` / `Shell VM` 规则三者完全共用 |
| Scoped context locals | `provides(key, value)` / `consume(key)` / `requireConsume(key)` | 把"页面内所有 Page 都要拿的东西"（首选**就是这个页面的 Mavericks Shell VM**）一次性放在 assembly scope，子级用 `requireConsume` 取，零构造参数透传；类型化 key，跨模块不会撞名 |
| **View-tree 访问** | `view.findPageContext()` / `view.requirePageContext()` | 任何一个 N 层深的自定义子 View / 内嵌 RecyclerView 的 ViewHolder 都能"沿 parent 链找到最近 Page 的 PageContext"，从而 `requireConsume(ShellVMKey)` 直接拿 VM——不再需要 binder/adapter 一层层把 VM 或 callback 透传进去；与 AndroidX 的 `ViewTreeLifecycleOwner` 同款机制 |
| 多槽位挂载 | `+MyPage() at R.id.slot_xxx` | 同一个布局想塞多个 Page、又不想都堆进 `LinearLayout`；缺槽位时**install 阶段抛错**，比运行时空指针好定位 |
| Host 驱动 replace | `assembly.replace { … }` | 登录成功/AB 切换/抽屉切换等"结构性变化"，由**宿主**整体重组当前 Assembly；触发条件**必须**从 Mavericks 状态来（`viewModel.onEach(State::structuralFlag)`），不能由 Page 自己经事件总线请求——保证旋屏/进程死后重建后结构正确 |
| 列表渲染（单类型） | `ListPage<T>(itemsFlow, ItemBinder)` / `ItemBinder<T>` | 列表行复用父 Page 的 `PageContext`（context transparency），1000 行 ≠ 1000 个生命周期；`itemsFlow` 推荐从 `viewModel.stateFlow.map { it.xxx }.distinctUntilChanged()` 派生，**不要**直接喂 repo 的 hot flow |
| 列表渲染（异构） | `MultiTypeListPage<T>(itemsFlow) { bind<C1>(...); bind<C2>(...) }` | 同一信息流里 `Note` / `Ad` / `LoadingRow` 等多种行混排时，按 `Class.isInstance` 路由到各自的 `ItemBinder`；`ItemBinder<T>` 接口不变，老代码零迁移；DiffUtil 跨类型一律视为不同 item，避免 RecyclerView 试图把 `AdRow` 视图重绑成 `NoteRow` |
| 重布局异步 inflate | `AsyncViewPage(layoutResId)` + `onViewInflated(view)` | 当某个 Page 的布局确实复杂（深层级 `ConstraintLayout`、多个 `<include>`、行内自定义 View 构造慢）且不是首屏 hero 时，用 `AsyncLayoutInflater` 把 inflate 推到后台线程，主线程只挂一个占位 `FrameLayout`；占位上立刻盖好 `PageContext`，深层子 View 在 inflate 之前也能 `findPageContext()`；detach guard 保证迟到的 inflate 不会回调到已销毁的 Page。**先量再换**——盲改对 above-the-fold 是负优化 |

**Scoped locals 的三层 fallback**（与三层 scope 一一对应）：

```
ScopedContainer:  pageLocal ──parent──▶ assemblyLocal ──parent──▶ hostLocal
读 (resolve):     先 page，找不到向上回退到 assembly，再到 host
写 (set/provides): 只写当前层，子作用域可以 shadow 父层但永远不会反向污染
```

调用约定：

- 模块顶层用 `pageContextKey<T>("ns.symbol")` 声明，**identity-keyed**（实例即身份），同名 key 不同 `val` 互不影响。
- 业务侧**强约定**：每个业务页面持有一个 _Shell ViewModel_（`MavericksViewModel<TState>`），并在 assembly 顶层 `provides(XxxShellViewModelKey, vm)`。所有子 Page / `ItemBinder` 用 `requireConsume(XxxShellViewModelKey)` 拿同一个实例——这就是该页面的"唯一真相源"。
- 仓库 (`Repository`)、点击 lambda 等**不要**再单独 `provides` 进 PageContext。它们是 VM 的实现细节，VM 暴露的应当是 `MavericksState` 切片和命令方法（`refresh()` / `like(id)` / `toggleBanner()`）。
- `consume(key)` 在缺失时返回 `null`；`requireConsume(key)` 缺失时抛带定位信息的错——业务侧默认用后者。

**Feed 走读：从 Activity 到行级 Binder**（`:features:feed` 的真实代码）：

```kotlin
// 1. 顶层 key（模块顶层 val）
val FeedShellViewModelKey = pageContextKey<FeedShellViewModel>("feed.shellViewModel")

// 2. State + ViewModel（单一 SoT）
data class FeedShellState(
    val notes: Async<List<Note>> = Uninitialized,
    val showBanner: Boolean = false,
) : MavericksState {
    val noteList: List<Note> get() = notes() ?: emptyList()
}

class FeedShellViewModel(
    initialState: FeedShellState,
    private val repo: FeedRepository,
) : MavericksViewModel<FeedShellState>(initialState) {
    init { refresh() }
    fun refresh() = suspend { repo.load() }.execute { copy(notes = it) }
    fun likeOne(id: String) = setState { copy(notes = Success(repo.like(id))) }
    fun toggleBanner()      = setState { copy(showBanner = !showBanner) }

    companion object : MavericksViewModelFactory<FeedShellViewModel, FeedShellState> {
        override fun create(vc: ViewModelContext, s: FeedShellState) =
            FeedShellViewModel(s, FeedRepository())
    }
}

// 3. Activity：建 VM → 装 assembly → 用 onEach 把"结构性状态"翻译成 replace
class FeedActivity : AppCompatActivity(R.layout.activity_feed), MavericksView {
    override fun invalidate() = Unit                    // 用 onEach 选择性订阅
    private lateinit var assembly: Assembly
    private lateinit var viewModel: FeedShellViewModel

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        viewModel = MavericksViewModelProvider.get(
            viewModelClass = FeedShellViewModel::class.java,
            stateClass     = FeedShellState::class.java,
            viewModelContext = ActivityViewModelContext(this, null),
            key            = "feed_shell",
        )
        installAssembly(showBanner = false)
        viewModel.onEach(FeedShellState::showBanner) { show ->
            if (!assembly.matchesBannerState(show)) installAssembly(show) // guard 见下
        }
    }

    private fun installAssembly(showBanner: Boolean) {
        val notesFlow = viewModel.stateFlow
            .map { it.noteList }.distinctUntilChanged()
        val block: AssemblyScope.() -> Unit = {
            provides(FeedShellViewModelKey, viewModel)
            provides(BannerStateKey, showBanner)                       // replace guard 用
            +FeedHeaderPage()                at R.id.feed_header_slot
            if (showBanner) +FeedBannerPage() at R.id.feed_body_slot
            +FeedListPage(notesFlow)          at R.id.feed_body_slot
            +FeedFooterPage()                 at R.id.feed_footer_slot
        }
        if (::assembly.isInitialized) assembly.replace(block) else assembly = assemble(block)
    }
}

// 4. 子 Page / Binder：consume VM，事件直接调方法，状态用 onEach 订阅
class FeedHeaderPage : ViewPage<HeaderBinding>(R.layout.page_feed_header, …) {
    override fun onViewCreated(b: HeaderBinding) {
        val vm = requireConsume(FeedShellViewModelKey)
        b.btnRefresh.setOnClickListener { vm.refresh() }
        b.btnBanner.setOnClickListener  { vm.toggleBanner() }
    }
}

object NoteItemBinder : ItemBinder<Note> {
    override fun createView(parent: ViewGroup, ctx: PageContext): View = … // 仅 inflate
    override fun bind(view: View, item: Note, position: Int, ctx: PageContext) {
        val vm = ctx.requireConsume(FeedShellViewModelKey)
        view.findViewById<TextView>(R.id.title).text = item.title
        view.setOnClickListener { vm.likeOne(item.id) }
    }
    override fun areItemsTheSame(old: Note, new: Note) = old.id == new.id
}

class FeedListPage(notes: Flow<List<Note>>) : ListPage<Note>(notes, NoteItemBinder)
```

为什么这样设计：

- **`replace` 的触发条件必须来自 state，不是来自事件**。`viewModel.onEach(State::showBanner)` 在旋屏/进程死后重建会自动 replay 当前 state，自然把结构补齐；如果改成 `hostBus.on<ToggleBannerRequested>` 这种边沿信号，重建后就丢了。这也是为什么 Page 拿不到 Assembly 句柄——结构性变化必须经过宿主、经过 VM 状态。
- **避免 `onEach → replace → onEach` 死循环**：`installAssembly` 在 assembly scope 里 `provides(BannerStateKey, showBanner)`；下一帧 `onEach` 触发时先 `assembly.matchesBannerState(show)` 比对一下旧值，相等则直接 return——这是 Pages 之外的纯宿主逻辑，业务 VM 里不沾这层细节。
- **`ListPage` 的 `itemsFlow` 从 `viewModel.stateFlow` 派生**（`.map { it.noteList }.distinctUntilChanged()`），不直接喂 `repo.someFlow`。这样列表的"现在显示什么"和 VM 状态严格一致——做 Loading 占位、做错误态、做乐观更新都只改 VM，列表自动跟上。

`replace` 的语义边界：

- 按**声明逆序**逐页 `performDetach` → 移除视图 → 清空 `assemblyLocal` 条目；`hostLocal` / `hostBus` / ViewModel store **不动**——所以 Page 关心的"我所在的宿主"那一面跨 replace 是稳定的。Activity 级别的 Mavericks ViewModel 也不会被 replace 干掉，因为它存活在 `ViewModelStoreOwner`（Activity）里，不在 `assemblyLocal` 里。
- 旧 Page 的 `LifecycleEventObserver` 在 `performDetach` 里**显式 remove**（v2 顺手修了一个潜在的观察者泄漏：见 `Page.hostObserver`）。
- 新组合的 `provides` 必须在新 block 内重新声明——这是有意的"明文优先"，让"替换后还看得到旧 provides"这种隐含状态不可能存在。
- Mavericks 的 `onEach` 用 `subscriptionLifecycleOwner`，对 Page 而言就是 Page 自己；`performDetach` 把 Page 推到 `ON_DESTROY`，订阅随之取消——所以"老 Page 还在监听"这种悬挂引用不会发生。
- Page 内的 `PageViewModel` 不被驱逐；同一 Page 类型再次出现时会拿到上一次的 VM（key 仍是 `{pageId}::{VMClass}`）。短生命的 bottom-sheet 类场景未来会加 `Assembly.dispose()` 显式释放。

**ListPage 与 ItemBinder 的协议要点**：

- 行级别**没有 Page 实体**：1000 行不会有 1000 个 `Lifecycle / ViewModel / ScopedEventBus`；`ItemBinder` 是无状态对象。
- 父 Page 的 `PageContext` 直接传给 `ItemBinder`，所以"行里要拿 Shell VM"完全不需要走构造函数链——用 `ctx.requireConsume(XxxShellViewModelKey)` 一行搞定。
- `itemsFlow` 用 `collectLatest` 订阅，慢消费者不会堆帧；`onDestroyView` 会主动断开 adapter 引用，避免 `replace` 周期间残留。
- 行内**只读 + 发命令**：不要在 `ItemBinder.bind` 里持有可变状态、不要在行里 `viewModel.onEach`。需要根据"行"维度反应状态，把那段状态做成 VM 里的 `Map<ItemId, X>` 切片，从 `itemsFlow` 派生出渲染数据。

**View-tree PageContext 访问**（自定义 View / 内嵌 RecyclerView 拿 VM 的标准方式）：

```kotlin
// 在 :features:* 里写一个可复用 widget——构造参数零业务耦合
class NoteActionBar(ctx: Context, attrs: AttributeSet?) : LinearLayout(ctx, attrs) {
    private var noteId: String? = null
    fun bind(noteId: String) { this.noteId = noteId }

    init {
        likeButton.setOnClickListener {
            // 沿 view.parent 链向上找最近的 PageContext 钉子
            requirePageContext()
                .requireConsume(FeedShellViewModelKey)
                .likeOne(noteId ?: return@setOnClickListener)
        }
    }
}
```

谁负责钉？两个地方各一次：

- `Page.performAttach` 在 `materialize()` 返回的 view 上 `setTag(R.id.assemblekit_page_context_tag, ctx)`；`performDetach` 清掉，防止 view 被外部 row pool / 截图工具缓存时把 host 引用拖住。
- `ListPage` 内部 adapter 在 `onCreateViewHolder` 给每个 row 的 `itemView` 也钉一份父 Page 的 PageContext——这样 row 里再深的 view（包括嵌套 RecyclerView 的内层 ViewHolder.itemView，因为它必然挂在某个 row 的子树里）一路 `view.parent` 走上去都能命中。

可运行的端到端示例在 `:features:feed` 里：

- [`NoteActionBar`](../features/feed/src/main/java/com/demo/features/feed/widget/NoteActionBar.kt) 是 row 内 depth-3 的"Like / Share"按钮条，用 `requirePageContext()`（mandatory，失败响亮）。
- [`RelatedTagsCarousel`](../features/feed/src/main/java/com/demo/features/feed/widget/RelatedTagsCarousel.kt) 里嵌套了一个独立的 `RecyclerView`，单个 tag chip 落在 depth-5 的 ViewHolder.itemView 里，用 `findPageContext()`（nullable，便于 preview 不崩）。
- 两边点击都打到同一个 `FeedShellViewModel`，结果落在 `FeedShellState.lastShared` / `lastTag` 上，由 **不同 slot 的** `FeedFooterPage` 渲染——这是"深处确实拿到的是当前 host 的 shell VM"的活体证据。

为什么用这个而不是 DI 框架：

- **作用域对齐**：`findPageContext()` 拿到的就是"当前所在那个 Page 的 PageContext"，进而是"当前所在那个 Assembly / 当前那个 host"。DI 容器拿到的是全局某个实例——在多个同类型 host 共存（多 Activity / SplitScreen）时这是 silent bug。
- **不给 MVI 留后门**：Koin/Hilt 让 view 能直接拿到 repository，结果就是有人在 view 里直接调 `repo.markRead(id)`，Shell VM 永远看不见这次 mutate → 状态漂移。`findPageContext()` 的唯一路径是 PageContext → key → VM，命令只能落在 VM 上。
- **轻**：零运行时反射、零代码生成、零 module-graph 配置；规则文档详见 [`docs/mvi-rules.md`](mvi-rules.md) § "Why not Koin/Hilt"。

**当前刻意不支持的能力**（以及怎么绕开）：

- 跨 Assembly 通信（同一宿主里两个 Assembly 互发事件）——**不允许**。让宿主当中转：两个 Assembly 都向 `hostBus` 发，宿主用 `hostBus.on { … }` 决定路由。
- 异步 inflate 自动开启——`AsyncViewPage` 必须**显式**选；框架不会偷偷把同步 inflate 替换成异步，因为对 above-the-fold 的 hero Page 这会把首帧空白时长摆上台面。先 trace 再换。
- **统一的 `DataLoadManager` / `IDataPreloader`（不引入，且短期不计划引入）**——参考过 Terpsi 同名能力，结论是它把 5 个不同的关注点糊进一个模块（异步流入 UI、三态、自动取消、缓存、跨屏预加载/in-flight 去重）。前 4 项 Mavericks `Async<T>` + `viewModelScope` + Repository 已经吃掉；只剩"跨屏预加载"和"in-flight 共享"是真空白，而这个空白在仓库现状里**没有任何真实使用场景**。等真的出现，就在对应 Repository 里加一个 `ConcurrentHashMap<K, Deferred<V>> + prefetch(k) / load(k)` 即可（约 10 行）；只有当这类重复出现 10+ 次且缓存策略可统一时，才值得抽 `Prefetcher<K, V>` 工具类——并且仍然**不**进 `PageContext`、**不**起新模块、**不**接管"业务能力"，只做 key 维度的请求合并。任何"我想给数据加载抽个框架"的提案，都要先在 PR 描述里逐条回答这一段。

> **已经在 v2.1 落地**：异构列表（`MultiTypeListPage<T>` + 类型路由）、重布局异步 inflate（`AsyncViewPage` + `AsyncLayoutInflater`）。Shell VM 在单屏功能堆叠后体积失控的拆分指南见 [`docs/sharding-shell-vm.md`](sharding-shell-vm.md)。
>
> **v2.2 已落地**：Compose 真实接入——[`:foundations:assemblekit-compose`](../foundations/assemblekit-compose/README.md) 模块提供 `ComposablePage`，与 `ViewPage` / `AsyncViewPage` 互为平级；通过 `LocalPageContext`（`CompositionLocal<PageContext?>`）把 `PageContext` 桥进 Composable，Composable 端用 `composeRequireConsume(XxxShellViewModelKey)` 拿到 Shell VM——跟 View 世界 `view.requirePageContext().requireConsume(...)` 完全同构。`Page.materialize()` 同步从 `internal abstract` 放宽到 `protected abstract`，原因是 Kotlin `internal` 跨 Gradle module 不能 override（详见 [`Page.kt`](../foundations/assemblekit/src/main/java/com/demo/foundations/assemblekit/Page.kt) 内的注释）。Compose 编译器扩展、BOM 与 `mavericks-compose` 通过 `demo.android.foundation.compose` convention plugin 集中管理，**不写 Compose 的模块**（`:bizlibs:*` 全部、`:features:*` 走 XML 的 page）**不引入任何 Compose 依赖**，编译时间无回退。

#### 容器层 vs 内容层：`:foundations:slidepane` 与 AssembleKit 的正交关系

业务上"主框架三屏滑动（中间 Home / 左滑 Profile / 右滑 Messages）"是个高度容易把容器和内容耦在一起的场景。Demo 里把它拆成两个互不依赖的框架：

| 层 | 模块 | 职责 | 跟 AssembleKit 的关系 |
| --- | --- | --- | --- |
| 容器层 | [`:foundations:slidepane`](../foundations/slidepane) | `SlidePaneContainer`（`FrameLayout` + `ViewDragHelper`）做水平拖拽 + 视差/蒙层动画，`PaneProvider` SPI 描述"我是哪个槽（CENTER/START/END）、我提供哪个 Fragment"，`PaneRegistry` 注册 / 查询 | **完全无关**——不依赖 `:foundations:assemblekit`，也不假设 Pane 内容怎么实现（XML 直出、自家 MVVM、Compose 都可以） |
| 内容层 | [`:features:mainframe`](../features/mainframe) | 三个 Pane 各自是一个 `PageHostFragment`，内部用 `assemble {}` 把若干 Page 装到布局的多个槽位 | **完全 AssembleKit 范式**——每个 Pane 一个 Shell VM，所有 Page 通过 `requireConsume(XxxShellViewModelKey)` 拿同一个 VM |

```
SlidePaneContainer                ← :foundations:slidepane（容器层）
  ├── CENTER  : HomePaneHostFragment      ┐
  ├── START   : ProfilePaneHostFragment   ├─ 每个都是 PageHostFragment（:foundations:assemblekit）
  └── END     : MessagesPaneHostFragment  ┘    内部 assemble {} + 一个 Shell VM
```

**关键设计：Pane → Activity 命令走 Actions 接口 + `hostLocal`**

Pane 内部经常需要让宿主 Activity 做"非 Pane 自己能完成"的事——比如 Home Pane 的头像点击需要让 SlidePane 打开 Profile Pane。直接持有 `MainActivity` 引用会把 feature 耦死、也违反"feature 不应该知道容器细节"的边界。Demo 采取的方案是：

1. `:features:mainframe` 里声明三个细粒度接口：`HomePaneActions` / `ProfilePaneActions` / `MessagesPaneActions`，每个接口只暴露**业务意图**（`requestOpenProfile()` / `requestCloseProfile()` / `addScrollDirectionListener(...)`），不出现 `SlidePane` 字样。
2. 同级再声明 3 个 `PageContextKey<XxxPaneActions>`（即 `MainframeActionKeys.kt`），供 Page 侧 `requireConsume(...)` 取用。
3. `XxxPaneHostFragment.onAttach` 里把 `context as XxxPaneActions` 写进自己的 `hostLocal[XxxPaneActionsKey]`，子 Page 通过 `requireConsume(XxxPaneActionsKey)` 拿。
4. `MainActivity` 实现这 3 个接口，方法体里翻译成 `SlidePaneContainer.openSlot(PaneSlot.START)` / `closeAll()` / `addScrollDirectionListener(...)` 等容器调用。

这样：

- Pane 不知道 SlidePane 的存在（只知道 Actions 接口）。
- SlidePane 不知道 AssembleKit 的存在（只知道 `PaneProvider` 给个 Fragment）。
- 容器策略改用别的（比如换成 `ViewPager2`），Pane 实现一行不动；Pane 改用别的 UI 范式（Compose / 自家 MVVM），SlidePane 也一行不动。

**Page 抽象选择按需要走，不要框架化**：mainframe 的 Pages 在三种 `Page` 子类里按需选择，没有"必须全用 ListPage"这种铁律——

| Page | 选哪个 | 原因 |
| --- | --- | --- |
| `HomeTopBarPage` / `HomeBottomBarPage` / `ProfileTopBarPage` / `MessagesTopBarPage` | `ViewPage` | 普通静态布局 |
| `HomeTabsPage` | `ListPage<HomeTabRow>` + `HomeTabBinder` | 单类型水平 tab 列表，正是 `ListPage` 的甜区 |
| `HomeFeedPage` / `ProfileContentPage` | 自定义 `ViewPage` 包 `RecyclerView` | 需要 `SwipeRefreshLayout` 外壳 + `StaggeredGridLayoutManager(2)` + 行 `isFullSpan` + 动态列宽——`ListPage`/`MultiTypeListPage` 故意让 `onCreateView`/`onViewCreated` 是 `final` 不允许定制布局，这种"列表 + 框 + 调参"的复合需求**应当**走自定义 `ViewPage`，框架不为它开特殊口子（防止 ListPage 沦为 god class）|
| `MessagesListPage` | `MultiTypeListPage<MessageRow>` | 信息流里 `Section`（"通知" / "私信"标题行）和 `Entry`（具体消息行）两种行混排——`MultiTypeListPage` 的标准甜区案例 |

Page 维度的"选择题"留在业务侧，是 AssembleKit v2 拆分 `ListPage` / `MultiTypeListPage` / `ViewPage` / `AsyncViewPage` / `ComposablePage` 这一组平级抽象的目的本身。`:features:mainframe` 五种用法各占一类（含两个"框架不直接覆盖、必须走自定义 ViewPage"的真实案例），是这套抽象选型是否够用的活体证据。

### 2. 依赖边界校验

`./gradlew checkDependencyRules` 遍历所有子模块的声明依赖（`api` / `implementation` / `compileOnly` / `runtimeOnly` / `testImplementation` / `androidTestImplementation` / `debugImplementation` / `releaseImplementation`），按规则表逐项校验，违反即抛 `GradleException` 并列出所有违规边。

### 3. 依赖图生成

`./gradlew generateDependencyGraph` 输出：

- `docs/dependency-graph.json`：机器可读（用于 affected modules 分析）。
- `docs/dependency-graph.dot`：Graphviz `digraph`，可用 `dot -Tpng` 渲染。
- `docs/dependency-graph.html`：免依赖的静态 HTML，按层着色，可用浏览器直接打开。

### 4. Affected modules 分析

`tools/affected-modules/affected_modules.py --base main` 通过 `git diff` 找出变更文件、映射到模块、再借助反向依赖图找出所有受影响模块，输出 `changedModules` / `affectedModules` / `suggestedGradleTasks`。CI 拿到这份 JSON 后只需要对 `affectedModules` 执行编译/单测即可，无需全量构建。

### 5. 文档同步契约（docs-sync）

工程治理的最后一公里是**避免代码与文档脱节**。规则与执行：

- **入口**：[`AGENTS.md` § Rule 0](../AGENTS.md#rule-0-docs-sync-contract-mandatory) 给 AI agent + 人类的工作规则
- **声明式映射**：[`tools/docs-sync/docs-sync-rules.json`](../tools/docs-sync/docs-sync-rules.json) 8 条规则，每条声明"触发路径 → 必须同步的 md"
- **人类详述版**：[`docs/doc-sync-rules.md`](doc-sync-rules.md) 每条规则的理由 / 失败例 / 豁免方式
- **校验器**：[`tools/docs-sync/check_docs_sync.py`](../tools/docs-sync/check_docs_sync.py) 与 `affected_modules.py` 同款风格（pure stdlib + git diff + JSON 输出），支持 `--strict`（CI）和默认 warn-only（本地）
- **本地钩子**：`bash tools/docs-sync/install-hooks.sh` 装一个**只 warn 不阻断**的 post-commit hook
- **逃生舱**：commit message 中 `[docs-skip]` 或 `[docs-skip:R3-new-module]`，但必须在 PR 描述里写清理由

逻辑非常朴素：变更集 = `git diff --name-only base...HEAD ∪ 当前工作区`；对每条规则，若 `triggers` 命中而 `requires_*` 没人改，就报违规。`diff_must_contain` 字段用来做"二阶过滤"——比如 `settings.gradle.kts` 只有真改了 `include(...)` 才算"新增模块"，避免误报。

> 跟 `checkDependencyRules` 一样，这条规则也是**机械检查**：写规则的人付一次成本，所有后来者自动受益；新规则的添加流程见 [`docs/doc-sync-rules.md` § 规则本身怎么演化](doc-sync-rules.md#规则本身怎么演化)。

## 目录结构

```
monorepo-demo/
├── AGENTS.md                   # Agent / 人类工作规则（R0 docs-sync 契约）
├── app/                        # 应用壳
├── features/                   # 业务 feature
│   ├── login/                  # 经典 assemble { } 三段式（v1 标准案例）
│   ├── home/                   # SPI 演示 + Router 入口（含 "Open Feed" / "Open Mainframe"）
│   ├── profile/
│   ├── feed/                   # AssembleKit v2 综合演示：
│   │                           #   ListPage + provides/consume + at() + replace
│   └── mainframe/              # SlidePane + AssembleKit 三屏滑动主框架综合演示：
│                               #   3 个 PageHostFragment / 3 个 Shell VM / 5 种 Page 用法
│                               #   (ViewPage / ListPage / MultiTypeListPage / 自定义 ViewPage 含 RecyclerView)
├── bizlibs/                    # 业务库
│   ├── account/
│   └── user/
├── foundations/                # 平台能力
│   ├── common/
│   ├── network/
│   ├── storage/
│   ├── router/
│   ├── analytics/
│   ├── ui/
│   ├── communicate/            # SPI：跨层反向通信（feature/bizlib ← app）
│   ├── assemblekit/            # Page / Assembly DSL + Mavericks MVI 集成
│   │                           # v2: ViewPage / AsyncViewPage
│   │                           #     ListPage / MultiTypeListPage
│   │                           #     + scoped locals (provides/consume)
│   │                           #     + per-page at(R.id) + Assembly.replace { }
│   ├── assemblekit-compose/    # 可选 · Compose 桥：ComposablePage + LocalPageContext
│   │                           # 不写 Compose 的模块零成本不依赖
│   └── slidepane/              # 水平三槽位滑动容器（SlidePaneContainer + PaneProvider SPI）
│                               # 与 :foundations:assemblekit 正交、互不依赖
├── third-party/                # 三方 / 适配
│   └── logger/
├── build-logic/                # Convention plugins（独立 included build）
│   └── convention/
├── tools/
│   ├── affected-modules/affected_modules.py   # 增量构建影响范围分析
│   └── docs-sync/                              # 代码 → 文档 同步校验（R0）
│       ├── docs-sync-rules.json
│       ├── check_docs_sync.py
│       └── install-hooks.sh
├── docs/
│   ├── architecture.md
│   ├── module-rules.md
│   ├── doc-sync-rules.md                       # R0 人类详述版
│   ├── dependency-graph.{json,dot,html}        # 由 Gradle 任务生成
└── .github/workflows/ci.yml    # CI：docs sync + 边界校验 + 依赖图 + assembleDebug
```
