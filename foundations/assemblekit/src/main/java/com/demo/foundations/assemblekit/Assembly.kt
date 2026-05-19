package com.demo.foundations.assemblekit

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import com.demo.foundations.assemblekit.bus.ScopedCommandBus
import com.demo.foundations.assemblekit.bus.ScopedEventBus
import com.demo.foundations.assemblekit.local.ScopedContainer
import com.demo.thirdparty.logger.Logger
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.atomic.AtomicInteger

/**
 * "在同一个屏幕里一起出现的那批东西" 的单位。
 *
 * 一个 [Assembly] 诞生于某个宿主（`Activity` / `Fragment`）之内，持有一组 [Page]。
 * Assembly 拥有：
 *
 *  - 自己**独立**的生命周期，从宿主镜像而来
 *  - 自己**独立**的 [ScopedEventBus] 和 [ScopedCommandBus]（仅供兄弟 Page 用）
 *  - 用于挂载 Page view 的容器 `ViewGroup`
 *
 * Assembly 必须通过 [assemble] 构造——不要直接 `new Assembly(...)`——这样 DSL 才能
 * 按正确顺序收集并安装 Page。
 *
 * 同一宿主下的两个 assembly **互相看不到对方的 bus**，这是有意为之：如果它们需要
 * 通信，请走宿主 bus。这样屏幕变大时 fan-out 的范围始终是可预期的。
 */
class Assembly internal constructor(
    val host: PageHost,
    /**
     * 默认容器：所有不使用 `at(R.id.…)` DSL 把自己钉到具体槽位上的 Page，
     * 都会被挂到这里。
     *
     * 两种合法配置：
     *  - `container` 提供、没有任何 `at(...)` 调用——经典的"全堆在一个盒子里"。
     *  - `container = null`、每个 Page 都用 `at(...)`——多槽位布局。
     *
     * 混用也没问题：被钉的 Page 挂到自己指定的位置，其余的统一堆进默认容器。
     * 如果某个 Page 既没被钉、默认容器也是 null，install 阶段 attach 会直接抛，
     * 错误信息会指明两种修复方向。
     */
    val container: ViewGroup?,
    /**
     * 当 [container] 是 [LinearLayout] 时，Page view 堆叠的方向。
     * 对 `FrameLayout` / `ConstraintLayout` 这种由 Page 自己定位的容器无效。
     */
    val orientation: Int = LinearLayout.VERTICAL,
) {

    // ------------------------------------------------------------------
    // 生命周期（从宿主镜像）
    // ------------------------------------------------------------------

    private val lifecycleRegistry = LifecycleRegistry(host)
    val lifecycle: Lifecycle get() = lifecycleRegistry

    // 宿主走到 ON_DESTROY 时取消的 scope。
    val scope: CoroutineScope = host.lifecycleScope

    // ------------------------------------------------------------------
    // 总线
    // ------------------------------------------------------------------

    private val assemblyId: String = run {
        val c = container
        val tag = when {
            c == null -> "noDefaultContainer-${ASSEMBLY_COUNTER.incrementAndGet()}"
            c.id != View.NO_ID -> c.id.toString()
            else -> c.hashCode().toString()
        }
        "asm-${host.hostId}-$tag"
    }

    val bus: ScopedEventBus = ScopedEventBus(tag = "AssemblyBus($assemblyId)")
    val commands: ScopedCommandBus = ScopedCommandBus(tag = "AssemblyCmd($assemblyId)")

    /**
     * Assembly 级 "locals" 容器。以 [PageHost.hostLocal] 为父，所以宿主上 provide 的
     * 任何值在这里（及其下面的每个 Page）通过 [ScopedContainer.resolve] 都能看到。
     *
     * 由 `assemble {}` DSL 块里的 `provides(key, value)` 调用填充；Page 通过
     * `consume(key)` 读取。
     */
    val assemblyLocal: ScopedContainer =
        ScopedContainer.child(parent = host.hostLocal, debugName = "asmLocal($assemblyId)")

    // ------------------------------------------------------------------
    // Page 集合
    // ------------------------------------------------------------------

    private val specs = mutableListOf<MountSpec>()
    private val attached = mutableListOf<Page>()
    // 记录每个 page 当初挂到了哪个 ViewGroup，replace() 时即使槽位不同
    // 也能精确地从对应槽位移除 view。
    private val pageMountTargets = mutableMapOf<Page, ViewGroup>()
    private var installed = false

    /** 已挂载 page 的只读快照，按声明顺序。 */
    val pagesSnapshot: List<Page> get() = attached.toList()

    internal fun add(spec: MountSpec) {
        check(!installed) { "Cannot add Page to an already-installed Assembly. Use replace { ... }." }
        specs += spec
    }

    /**
     * 按声明顺序把队列里的每个 Page inflate + attach。由 [assemble] 在 DSL 块跑完
     * 后调用；外部调用方不要手动调它。
     */
    internal fun install() {
        check(!installed) { "Assembly already installed" }
        installed = true

        // 把宿主的生命周期同步到自己的 registry，然后持续追踪。
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        host.lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START,
                    Lifecycle.Event.ON_RESUME,
                    Lifecycle.Event.ON_PAUSE,
                    Lifecycle.Event.ON_STOP,
                    Lifecycle.Event.ON_DESTROY,
                    -> lifecycleRegistry.handleLifecycleEvent(event)
                    else -> Unit
                }
            },
        )

        specs.forEachIndexed { index, spec ->
            attachPage(spec, index)
        }
    }

    private fun attachPage(spec: MountSpec, index: Int) {
        val page = spec.page
        val mountTarget = spec.resolveContainer(host, container)
        val pageId = derivePageId(page, index)
        // 每个 page 拿到自己的 bus + 一个由 assembly scope 派生出来的协程 scope，
        // 这样将来要加 "只换一个 page" 的能力时，被换掉的 page 的订阅者不会泄漏。
        val pageBus = ScopedEventBus(tag = "PageBus($pageId)")
        val pageCommands = ScopedCommandBus(tag = "PageCmd($pageId)")

        // 每个 Page 拿到自己的 local 容器，挂到 assembly 的（assembly 的又挂到宿主的）。
        // 查找时 ScopedContainer.resolve 会自动沿这条链回溯。
        val pageLocal = ScopedContainer.child(parent = assemblyLocal, debugName = "pageLocal($pageId)")

        val ctx = PageContext(
            host = host,
            assembly = this,
            pageId = pageId,
            // 用 page 自己的 lifecycleScope（基于 LifecycleRegistry），
            // 它会在 performDetach() 里通过 ON_DESTROY 取消。
            pageScope = page.lifecycleScope,
            assemblyScope = scope,
            hostScope = host.lifecycleScope,
            pageBus = pageBus,
            assemblyBus = bus,
            hostBus = host.hostBus,
            pageCommands = pageCommands,
            assemblyCommands = commands,
            hostCommands = host.hostCommands,
            pageLocal = pageLocal,
            assemblyLocal = assemblyLocal,
            hostLocal = host.hostLocal,
            hostViewModelStoreOwner = host,
        )

        try {
            val view = page.performAttach(ctx, mountTarget)
            mountTarget.addView(view, defaultLayoutParams(mountTarget))
            attached += page
            pageMountTargets[page] = mountTarget
            Logger.d(
                LOG_TAG,
                "[$assemblyId] attached page #$index id=$pageId into ${describe(mountTarget)}",
            )
        } catch (t: Throwable) {
            Logger.e(LOG_TAG, "[$assemblyId] failed to attach page #$index: ${t.message}")
            throw t
        }
    }

    // ------------------------------------------------------------------
    // 重组：由宿主驱动的结构性变更
    // ------------------------------------------------------------------

    /**
     * 拆掉当前已挂载的每个 Page，再按 [block] 声明的组合重新安装。
     * 宿主自身的生命周期、scope、ViewModelStore、[hostLocal] / [hostBus]
     * **不会**被动；动到的只有这个 Assembly 的 Page 和 [assemblyLocal] 里的条目。
     *
     * 为什么这个方法挂在 Assembly 上、且只允许宿主调用：
     *  - Page 取不到、也刻意取不到 Assembly 实例——不允许 Page 互相替换兄弟。
     *    让 Page 改组合，就把 "page 是一小块 UI 切片" 的契约滑坡成 "page 是路由器"，
     *    那正是 AssembleKit 存在以避开的 Fragment 陷阱。
     *  - 宿主本来就通过生命周期 + 第一次 `assemble {}` 拥有 "当前屏幕长啥样" 的决策权。
     *    `replace` 是同一棵决策树上的第二个入口：
     *    "从这个事件起，屏幕改成长这样"。
     *
     * 典型用法：Activity 监听 `hostBus` 上的事件，借此重塑自己的 assembly。
     *
     * ```kotlin
     * // 在 LoginActivity 里，例如登录成功后：
     * hostBus.on<LoginEvent.LoginFinished>(lifecycleScope) {
     *     loginAssembly.replace {
     *         +SuccessHeaderPage() at R.id.slot_header
     *         +ContinueButtonPage() at R.id.slot_bottom
     *     }
     * }
     * ```
     *
     * 取舍：
     *  - 被移除 Page 的 ViewModel 会一直留在宿主的 ViewModelStore 里，直到宿主销毁。
     *    对长寿命 assembly 一般不算泄漏；对短寿命 bottom-sheet 可能会涨。
     *    未来的 `Assembly.dispose()` 会主动驱逐。
     *  - assemblyLocal 条目会在新 block 跑之前被清空，新组合的起点是
     *    "宿主提供的那些" + 它自己新 provide 的那些。这是一个可预测的选择——
     *    要让一个值在 replace 之后还能存活，请挂到 hostLocal 上。
     */
    fun replace(block: AssemblyBuilder.() -> Unit) {
        check(installed) {
            "Assembly.replace() called before initial install — " +
                "use assemble { … } for the first composition."
        }

        // 按声明顺序的反序拆当前 page，这样兄弟之间的依赖能干净地反向解除。
        for (page in attached.asReversed()) {
            val mountTarget = pageMountTargets[page]
            val view = page.view
            if (view != null && mountTarget != null) {
                try {
                    mountTarget.removeView(view)
                } catch (t: Throwable) {
                    Logger.w(LOG_TAG, "[$assemblyId] removeView during replace failed: ${t.message}")
                }
            }
            try {
                page.performDetach()
            } catch (t: Throwable) {
                Logger.w(LOG_TAG, "[$assemblyId] performDetach during replace failed: ${t.message}")
            }
        }
        attached.clear()
        pageMountTargets.clear()

        // 给新组合一份干净的 provide 表；hostLocal 不动，宿主接好的东西都还在。
        assemblyLocal.clearLocalEntries()

        // 从新 builder block 重新收集 spec，然后重新 attach。
        specs.clear()
        AssemblyBuilder(this).block()
        val newSpecs = specs.toList()
        newSpecs.forEachIndexed { index, spec ->
            attachPage(spec, index)
        }

        Logger.d(LOG_TAG, "[$assemblyId] replaced composition (${newSpecs.size} pages)")
    }

    private fun derivePageId(page: Page, index: Int): String =
        "${host.hostId}::${page.javaClass.simpleName}#$index"

    private fun defaultLayoutParams(target: ViewGroup): ViewGroup.LayoutParams = when (target) {
        is LinearLayout -> LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        else -> ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun describe(v: ViewGroup): String =
        if (v.id != View.NO_ID) "${v.javaClass.simpleName}(#${Integer.toHexString(v.id)})"
        else v.javaClass.simpleName

    companion object {
        private const val LOG_TAG = "Assembly"
        private val ASSEMBLY_COUNTER = AtomicInteger(0)
    }
}

