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
+-----------------------------------------------------------------+
|  :bizlibs:account   :bizlibs:user                               |
+-----------------------------------------------------------------+
|  :foundations:common   :foundations:network   :foundations:storage|
|  :foundations:router   :foundations:analytics :foundations:ui    |
|  :foundations:communicate (SPI: feature/bizlib ↔ app 反向通信)    |
|  :foundations:assemblekit (Page / Assembly DSL · Mavericks MVI)   |
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
        ▼  Router.navigate(PROFILE)
ProfileActivity (:features:profile)
```

> **要点：** Login → Home → Profile 之间的跳转**不**通过相互依赖，而是通过 `:foundations:router` 实现的字符串路由。这让“`:features:*` 不能依赖其他 `:features:*`”这条规则在编译期和 `checkDependencyRules` 任务里都能被守住。

## 工程治理能力

### 1. Convention Plugins

`build-logic/convention` 中实现了 6 个 plugin：

| Plugin id | 用途 |
| --- | --- |
| `demo.android.application` | `:app` 唯一使用，配置 application、versionCode/Name |
| `demo.android.feature` | `:features:*` 使用，开启 viewBinding |
| `demo.android.bizlib` | `:bizlibs:*` 使用 |
| `demo.android.foundation` | `:foundations:*` 使用 |
| `demo.kotlin.library` | 纯 Kotlin/JVM 模块（如 `:third-party:logger`） |
| `demo.dependency.guard` | 注册根任务：`checkDependencyRules` / `generateDependencyGraph` |

> 新增的 `:foundations:communicate` 和 `:foundations:assemblekit` 都沿用 `demo.android.foundation`，无需新 plugin。

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

### 2. 依赖边界校验

`./gradlew checkDependencyRules` 遍历所有子模块的声明依赖（`api` / `implementation` / `compileOnly` / `runtimeOnly` / `testImplementation` / `androidTestImplementation` / `debugImplementation` / `releaseImplementation`），按规则表逐项校验，违反即抛 `GradleException` 并列出所有违规边。

### 3. 依赖图生成

`./gradlew generateDependencyGraph` 输出：

- `docs/dependency-graph.json`：机器可读（用于 affected modules 分析）。
- `docs/dependency-graph.dot`：Graphviz `digraph`，可用 `dot -Tpng` 渲染。
- `docs/dependency-graph.html`：免依赖的静态 HTML，按层着色，可用浏览器直接打开。

### 4. Affected modules 分析

`tools/affected-modules/affected_modules.py --base main` 通过 `git diff` 找出变更文件、映射到模块、再借助反向依赖图找出所有受影响模块，输出 `changedModules` / `affectedModules` / `suggestedGradleTasks`。CI 拿到这份 JSON 后只需要对 `affectedModules` 执行编译/单测即可，无需全量构建。

## 目录结构

```
monorepo-demo/
├── app/                        # 应用壳
├── features/                   # 业务 feature
│   ├── login/
│   ├── home/
│   └── profile/
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
│   └── assemblekit/            # Page / Assembly DSL + Mavericks MVI 集成
├── third-party/                # 三方 / 适配
│   └── logger/
├── build-logic/                # Convention plugins（独立 included build）
│   └── convention/
├── tools/
│   └── affected-modules/affected_modules.py
├── docs/
│   ├── architecture.md
│   ├── module-rules.md
│   ├── dependency-graph.{json,dot,html}   # 由 Gradle 任务生成
└── .github/workflows/ci.yml    # CI：边界校验 + 依赖图 + assembleDebug
```
