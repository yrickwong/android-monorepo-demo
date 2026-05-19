package com.demo.foundations.assemblekit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.airbnb.mvrx.MavericksView
import com.demo.foundations.assemblekit.local.PageContextKey
import com.demo.thirdparty.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/**
 * 单个、轻量级的 UI 切片：知道如何把自己具象化成 View，挂接 Mavericks ViewModel，
 * 并通过 scoped bus 与兄弟 Page 通信。
 *
 * `Page` 刻意**对渲染机制保持抽象**——真正"生产 View"的契约由具体子类型实现：
 *
 *  - [ViewPage]       —— 经典 XML / inflate 出来的 View（目前默认）
 *  - [AsyncViewPage]  —— 同 ViewPage，但通过 `AsyncLayoutInflater` 在非主线程 inflate
 *  - `ComposablePage` —— Jetpack Compose 载荷，位于姊妹模块
 *                       `:foundations:assemblekit-compose`（这样不使用 Compose 的
 *                       下游不必为 Compose 工具链付出代价）。KDoc 无法跨模块 @link，
 *                       所以这里特意写成纯文本引用。
 *
 * 下面所有东西——生命周期、savedstate、bus、Mavericks ViewModel 委托、宿主桥接——
 * 在三种子类型里都是一样的。
 *
 * 为什么不直接用 Fragment？
 *  - Page 不上回退栈。没有 FragmentManager、没有 transaction、没有 commit-now / commit-later
 *    这种区分。
 *  - Page 不会重复记账状态。配置变更完全交给 Mavericks 处理（`@PersistState` 等）。
 *  - Page 在单元测试里可以直接 `new` 出来；你只需要一个假的 [PageContext] 就能驱动
 *    整套接线。
 *
 * 生命周期模型：
 *
 *  ```
 *  attach(ctx)     -> Lifecycle.State.CREATED
 *  materialize     -> ... (ViewPage.onCreateView / ComposablePage.Content / AsyncViewPage.onViewInflated)
 *  hostStart       -> STARTED
 *  hostResume      -> RESUMED
 *  hostPause       -> STARTED
 *  hostStop        -> CREATED
 *  detach          -> DESTROYED   (pageScope 被取消、bus 不可达)
 *  ```
 */
abstract class Page(
    /**
     * 可选的显式 id。如果传 null，会从类名 + 在 assembly 里的槽位序号自动生成。
     * 只有当 Page 顺序变化时仍想保留同一个 Mavericks ViewModel 跨配置变更存活，
     * 才需要传入显式 id。
     */
    private val explicitId: String? = null,
) : LifecycleOwner, SavedStateRegistryOwner, MavericksView {

    // ------------------------------------------------------------------
    // 生命周期 / SavedState
    // ------------------------------------------------------------------

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    // ------------------------------------------------------------------
    // 上下文 / 身份
    // ------------------------------------------------------------------

    /**
     * 框架在 [performAttach] 时塞进来的环境对象。
     * attach 之前访问会抛——这是有意为之：子类在 [materialize] 跑起来之前不应假设
     * 环境已经就绪。
     */
    protected lateinit var context: PageContext
        private set

    /** 稳定 id，在所属 [Assembly] 内唯一。 */
    val pageId: String get() = if (::context.isInitialized) context.pageId else fallbackId()

    /**
     * 给框架内部辅助代码（比如 `pageViewModel()` 委托）用的 friend 风格访问器：
     * 既能拿到 host，又不必把 `protected context` 暴露给外部调用者。
     */
    @PublishedApi
    internal fun hostOrNullInternal(): PageHost? =
        if (::context.isInitialized) context.host else null

    /** Mavericks 用这个值来界定状态订阅的作用域。 */
    final override val mvrxViewId: String get() = pageId

    /**
     * 我们刻意**不使用** Mavericks 的 `invalidate()` 模式。
     * 所有状态订阅都应通过 `onEach` / `onAsync` 选择器表达——既更精确，
     * 也更高效（无关状态变化不会触发整页重绘）。
     */
    final override fun invalidate(): Unit = Unit

    private fun fallbackId(): String = explicitId ?: "${javaClass.simpleName}@${hashCode()}"

    // ------------------------------------------------------------------
    // 便捷 scope / bus 访问（仅在 attach 之后有效）
    // ------------------------------------------------------------------

    protected val pageScope: CoroutineScope     get() = context.pageScope
    protected val assemblyScope: CoroutineScope get() = context.assemblyScope
    protected val hostScope: CoroutineScope     get() = context.hostScope

    // ------------------------------------------------------------------
    // 子类扩展点
    // ------------------------------------------------------------------

    /**
     * 生产本 Page 的根 [View]。在 `ON_CREATE` 与宿主第一次 `ON_START` 之间被调用一次。
     *
     * 由具体子类型实现：
     *  - [ViewPage] inflate XML 并依次跑 `onCreateView` + `onViewCreated`
     *  - `ComposablePage`（位于 `:foundations:assemblekit-compose`）把一个
     *    `Content()` 可组合函数包进 `ComposeView`
     *
     * 返回的 View 由框架挂到 assembly 容器上；子类**不要**自己再 add 一遍。
     *
     * **可见性说明：** 用 `protected` 是为了让兄弟 Gradle 模块（特别是
     * `:foundations:assemblekit-compose`）能 `override` 它。
     * Kotlin 的 `internal` 在"仅限框架内部"这层语义上更合适，但它是按
     * Kotlin module 边界强制的——不同 Gradle 模块无法 override `internal` 声明。
     * 退而求其次的 `protected` 仍能保证业务代码看不到该方法（必须身处某个 `Page`
     * 子类里才能调用它），这就是我们实际需要的保证。
     */
    protected abstract fun materialize(inflater: LayoutInflater, parent: ViewGroup): View

    /**
     * 通用析构钩子。在 View 已经 detach、即将进入 [Lifecycle.State.DESTROYED] 之前调用。
     * [ViewPage] 会把自己的 `onDestroyView` 汇流到这里；持有其它资源（Compose
     * disposable、native 句柄……）的子类可以直接 override。
     */
    protected open fun onDestroy(): Unit = Unit

    // ------------------------------------------------------------------
    // Event / command 辅助
    // ------------------------------------------------------------------

    /** 监听本 Page 自己的 bus（页内事件）。detach 时自动取消。 */
    protected inline fun <reified E : Any> onPageEvent(
        noinline block: suspend (E) -> Unit,
    ): Job = context.pageBus.on(pageScope, block)

    /** 监听所属 [Assembly] 的 bus（兄弟 Page 之间的事件）。 */
    protected inline fun <reified E : Any> onAssemblyEvent(
        noinline block: suspend (E) -> Unit,
    ): Job = context.assemblyBus.on(pageScope, block)

    /** 监听宿主的 bus（Activity / Fragment 全局事件）。 */
    protected inline fun <reified E : Any> onHostEvent(
        noinline block: suspend (E) -> Unit,
    ): Job = context.hostBus.on(pageScope, block)

    /** 向同一 Assembly 里的兄弟 Page 广播。 */
    protected fun emitToAssembly(event: Any): Boolean = context.assemblyBus.emit(event)

    /** 向宿主广播（Activity / Fragment 全局）。 */
    protected fun emitToHost(event: Any): Boolean = context.hostBus.emit(event)

    // ------------------------------------------------------------------
    // Scoped locals（provides / consume）
    // ------------------------------------------------------------------

    /**
     * 解析 page→assembly→host 链路上任何一层 provide 过的值。
     * 若全部 scope 都没 provide 这个 key 则返回 `null`。等价于
     * `context.consume(key)`；这里加一个 inline 友好的快捷方法，
     * 让子类不用再去捅 `context`。
     */
    protected fun <T> consume(key: PageContextKey<T>): T? = context.consume(key)

    /** 同 [consume]，但 key 从未被 provide 过时会抛异常。 */
    protected fun <T> requireConsume(key: PageContextKey<T>): T = context.requireConsume(key)

    /**
     * Provide 一个**只有本 Page 可见**的值（以及该 Page 显式把自己的 [PageContext]
     * 传下去的嵌套消费者，比如 `ListPage` 里的 item）。"只为单个 Page 遮蔽父级"
     * 是一种常见需求——比如某个"预览"Page 想用一个假 repository，而兄弟 Page 仍能
     * 看到真 repository。
     *
     * 框架级或屏幕级的 provide，应该走 `assemble {}` DSL 或宿主的
     * `hostLocal[...] = …` setter。
     */
    protected fun <T> providesPage(key: PageContextKey<T>, value: T) {
        context.pageLocal[key] = value
    }

    // ------------------------------------------------------------------
    // 框架内部入口（由 Assembly 调用）
    // ------------------------------------------------------------------

    internal var view: View? = null
        private set

    /**
     * 指向 [bridgeHostLifecycle] 里注册的宿主生命周期 observer 引用，
     * 留着给 [performDetach] 反注册用。否则每次 [Assembly.replace] 重组都会泄漏
     * 一个 observer。
     */
    private var hostObserver: LifecycleEventObserver? = null

    internal fun performAttach(ctx: PageContext, parent: ViewGroup): View {
        require(!::context.isInitialized) { "Page $pageId already attached" }
        context = ctx

        // SavedStateRegistry 必须在任何消费者读取它之前被 restore。
        savedStateController.performRestore(null /* savedInstanceState 由宿主统一处理 */)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        val inflater = LayoutInflater.from(parent.context)
        val created = materialize(inflater, parent)
        view = created

        // 给 View 树打上 PageContext 戳，这样任何后代（自定义 View、嵌套 RecyclerView 内层
        // ViewHolder 等）都可以通过 `view.findPageContext()` 拿到当前 PageContext，
        // 而不必把它当成构造函数 / setter 参数往下传。契约对齐 AndroidX 的
        // ViewTreeLifecycleOwner 模式。
        created.setPageContext(ctx)

        // 镜像宿主当前的生命周期状态——如果 assembly 构建时宿主已经处于
        // STARTED/RESUMED，我们要同步把状态追上来，订阅者才能看到一致的事件序列。
        bridgeHostLifecycle(ctx.host)

        return created
    }

    internal fun performDetach() {
        try {
            onDestroy()
        } catch (t: Throwable) {
            Logger.w(LOG_TAG, "onDestroy threw for $pageId: ${t.message}")
        }
        // 在丢弃 view 引用之前先把 ViewTree 戳清掉，
        // 这样如果某些 View 被框架以外的地方缓存了（行池、截图工具），
        // 也不能继续把 PageContext / 宿主拖在内存里。
        view?.setPageContext(null)
        view = null

        // 把自己从宿主生命周期里反订阅，避免 Assembly.replace 反复跑后堆 observer。
        // 加 try 是因为 performDetach 也可能是宿主自己被销毁时跑的，此时 observer
        // 列表本身就在拆。
        hostObserver?.let { obs ->
            if (::context.isInitialized) {
                try {
                    context.host.lifecycle.removeObserver(obs)
                } catch (_: Throwable) {
                    /* 宿主已经没了，无所谓 */
                }
            }
        }
        hostObserver = null

        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }

    /**
     * 把宿主的生命周期事件转发进我们自己的 [lifecycleRegistry]，让绑定到 [pageScope]
     * 的 Mavericks 订阅 / 协程能收到一致的状态切换。
     *
     * 我们监听的是**宿主**（而不是 assembly），这样即使页面在 assembly 重组后才加入，
     * 宿主当时已经处于 RESUMED 状态，新加入的页面也能正确收到 STARTED/RESUMED 事件。
     */
    private fun bridgeHostLifecycle(host: PageHost) {
        // 先把状态追上来……
        val hostState = host.lifecycle.currentState
        if (hostState.isAtLeast(Lifecycle.State.STARTED) &&
            lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.CREATED)
        ) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }
        if (hostState.isAtLeast(Lifecycle.State.RESUMED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        // ……再追踪后续切换。保存 observer 引用以便 performDetach 反注册。
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START,
                Lifecycle.Event.ON_RESUME,
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                -> lifecycleRegistry.handleLifecycleEvent(event)
                Lifecycle.Event.ON_DESTROY -> performDetach()
                else -> Unit
            }
        }
        hostObserver = obs
        host.lifecycle.addObserver(obs)
    }

    @Suppress("unused")
    internal fun performSavedStateRestore(bundle: Bundle?) {
        savedStateController.performRestore(bundle)
    }

    companion object {
        private const val LOG_TAG = "Page"
    }
}
