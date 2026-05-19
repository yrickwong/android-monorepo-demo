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
 * The unit of "things that ship together on one screen".
 *
 * An [Assembly] is born inside a host (`Activity` / `Fragment`) and holds
 * a list of [Page]s. The assembly owns:
 *
 *  - its **own** lifecycle, mirrored from the host
 *  - its **own** [ScopedEventBus] and [ScopedCommandBus] (siblings only)
 *  - the container `ViewGroup` into which Page views are attached
 *
 * Assemblies are constructed via [assemble] — never `new Assembly(...)`
 * directly — so that the DSL can collect Pages and install them in the
 * correct order.
 *
 * Two assemblies inside the same host **do not see each other's buses**
 * by design: if they need to communicate, they go through the host bus.
 * This keeps fan-out predictable as a screen grows.
 */
class Assembly internal constructor(
    val host: PageHost,
    /**
     * Default container used by any Page that does NOT use the
     * `at(R.id.…)` DSL to pin itself onto a specific slot.
     *
     * Two valid configurations:
     *  - `container` set, no `at(...)` calls — classic stack-into-one-box.
     *  - `container = null`, every Page uses `at(...)` — multi-slot layout.
     *
     * Mixing is fine: pinned Pages mount where they ask, the rest stack
     * into the default container. If a Page has neither and there's no
     * default, attach throws at install time with a pointer to both
     * fixes.
     */
    val container: ViewGroup?,
    /**
     * Direction in which Page views are stacked when [container] is a
     * [LinearLayout]. Ignored for `FrameLayout` / `ConstraintLayout`-style
     * containers where Pages position themselves.
     */
    val orientation: Int = LinearLayout.VERTICAL,
) {

    // ------------------------------------------------------------------
    // Lifecycle (mirrored from host)
    // ------------------------------------------------------------------

    private val lifecycleRegistry = LifecycleRegistry(host)
    val lifecycle: Lifecycle get() = lifecycleRegistry

    // Scope cancelled when the host hits ON_DESTROY.
    val scope: CoroutineScope = host.lifecycleScope

    // ------------------------------------------------------------------
    // Buses
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
     * Assembly-level "locals" container. Chained to [PageHost.hostLocal]
     * as its parent so anything provided on the host is visible here
     * (and to every page underneath) via [ScopedContainer.resolve].
     *
     * Populated by the `provides(key, value)` calls inside the
     * `assemble {}` DSL block; pages can read via `consume(key)`.
     */
    val assemblyLocal: ScopedContainer =
        ScopedContainer.child(parent = host.hostLocal, debugName = "asmLocal($assemblyId)")

    // ------------------------------------------------------------------
    // Pages
    // ------------------------------------------------------------------

    private val specs = mutableListOf<MountSpec>()
    private val attached = mutableListOf<Page>()
    // Remember which ViewGroup we mounted each page into, so replace() can
    // remove the exact view from the exact slot even when slots differ.
    private val pageMountTargets = mutableMapOf<Page, ViewGroup>()
    private var installed = false

    /** Read-only snapshot of attached pages, in declaration order. */
    val pagesSnapshot: List<Page> get() = attached.toList()

    internal fun add(spec: MountSpec) {
        check(!installed) { "Cannot add Page to an already-installed Assembly. Use replace { ... }." }
        specs += spec
    }

    /**
     * Inflate and attach every queued Page, in declaration order. This is
     * called by [assemble] right after the DSL block runs; callers should
     * not invoke it manually.
     */
    internal fun install() {
        check(!installed) { "Assembly already installed" }
        installed = true

        // Sync the host's lifecycle into our own registry, then keep tracking.
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
        // Each page gets its own bus + its own coroutine scope derived from
        // the assembly scope, so we can later add "swap one page" semantics
        // without leaking subscribers from the replaced page.
        val pageBus = ScopedEventBus(tag = "PageBus($pageId)")
        val pageCommands = ScopedCommandBus(tag = "PageCmd($pageId)")

        // Each Page gets its own local container, chained to the assembly's
        // (which is itself chained to the host's). Lookups walk this chain
        // automatically via ScopedContainer.resolve.
        val pageLocal = ScopedContainer.child(parent = assemblyLocal, debugName = "pageLocal($pageId)")

        val ctx = PageContext(
            host = host,
            assembly = this,
            pageId = pageId,
            // Use the page's own lifecycleScope (LifecycleRegistry-backed),
            // which is cancelled in performDetach() via ON_DESTROY.
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
            // LayoutParams 处理策略（v2.1 修复）：
            //  1) 如果 Page 的 root view 已经带有 layoutParams——无论是 XML
            //     inflate(parent, false) 推出来的（带 parent 类型对应的
            //     LP 子类），还是 Page 自己 `apply { layoutParams = ... }`
            //     程序化创建的——一律**尊重**，不再覆盖。
            //  2) 仅当 view.layoutParams == null 时，才回退到框架默认
            //     ([defaultLayoutParams])。这种情况只会发生在：开发者
            //     用 `View(ctx)` / `inflate(layoutId, null)` 等方式构造
            //     view 又**没有**手动设置 LP——属于少数兜底场景。
            //
            // 修复背景：早期版本无条件 `addView(view, defaultLayoutParams(...))`，
            // 把 Page 显式声明的 MATCH×MATCH 直接踩成 MATCH×WRAP。对于
            // SwipeRefreshLayout / LinearLayoutManager 这种支持
            // auto-measure 的 root 没有可观察的症状；但当 root 是 bare
            // RecyclerView + StaggeredGridLayoutManager（isAutoMeasureEnabled
            // = false）时，WRAP 模式下高度直接坍缩成 0，整页空白。典型
            // 受害者：features/mainframe ProfileContentPage。
            val existingLp = view.layoutParams
            if (existingLp != null) {
                mountTarget.addView(view)
            } else {
                mountTarget.addView(view, defaultLayoutParams(mountTarget))
            }
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
    // Recomposition: host-driven structural change.
    // ------------------------------------------------------------------

    /**
     * Tear down every currently-attached Page and re-install the
     * composition declared in [block]. The host's own lifecycle, scope,
     * ViewModelStore, and [hostLocal] / [hostBus] are **not** affected;
     * only this Assembly's pages and its [assemblyLocal] entries are.
     *
     * Why this lives on Assembly (and is callable only by the Host):
     *  - Page does not, and intentionally cannot, reach the Assembly
     *    instance — Pages aren't allowed to swap their siblings out from
     *    under each other. Letting them mutate composition turns the
     *    "page is a small UI slice" contract into "page is a router",
     *    which is exactly the Fragment trap AssembleKit exists to avoid.
     *  - The host already owns the decision of "what is on screen right
     *    now" via its lifecycle + the initial `assemble {}` block.
     *    `replace` is the second entry point of that same decision tree:
     *    "from this event onward, the screen looks like THIS instead".
     *
     * Typical use: Activity listens for an event on `hostBus` and reacts
     * by reshaping its assembly.
     *
     * ```kotlin
     * // inside LoginActivity, e.g. after credentials succeed:
     * hostBus.on<LoginEvent.LoginFinished>(lifecycleScope) {
     *     loginAssembly.replace {
     *         +SuccessHeaderPage() at R.id.slot_header
     *         +ContinueButtonPage() at R.id.slot_bottom
     *     }
     * }
     * ```
     *
     * Trade-offs:
     *  - ViewModels of removed Pages remain in the host's ViewModelStore
     *    until the host is destroyed. For long-lived assemblies this is
     *    rarely a leak; for short-lived bottom-sheets it can grow. A
     *    future `Assembly.dispose()` will evict eagerly.
     *  - assemblyLocal entries are wiped before the new block runs, so
     *    the new composition starts from "whatever the host provided"
     *    plus its own provides. This is the predictable choice — if you
     *    want a value to survive a replace, put it on hostLocal.
     */
    fun replace(block: AssemblyBuilder.() -> Unit) {
        check(installed) {
            "Assembly.replace() called before initial install — " +
                "use assemble { … } for the first composition."
        }

        // Tear down current pages in reverse declaration order so any
        // sibling dependencies unwind cleanly.
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

        // Fresh provides surface for the new composition; hostLocal is
        // untouched so anything the host wired stays visible.
        assemblyLocal.clearLocalEntries()

        // Re-collect specs from the new builder block, then re-attach.
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

    /**
     * 兜底 LayoutParams：仅在 Page 的 root view **没有**自己声明 LP 时使用
     * （见 [attachPage] 中的分支）。
     *
     * 设计选择：
     *  - **LinearLayout（stack 模式）** —— `MATCH × WRAP`：每个 Page 是栈
     *    里的一格，把宽度撑满、高度按内容自适应是最常见的需求。
     *  - **非 LinearLayout（slot 模式，FrameLayout / ConstraintLayout 等）**
     *    —— `MATCH × MATCH`：槽位的尺寸由宿主布局事先约束好，Page
     *    塞进去就应该把槽位填满；如果开发者想要不同行为，请在
     *    `onCreateView` 里给 root view 显式 setLayoutParams——分支 1 会
     *    尊重你写下的任何尺寸。
     *
     * 之所以 slot 模式选 `MATCH × MATCH` 而不是历史上的 `MATCH × WRAP`：
     * 后者会让用裸 RecyclerView（无 SwipeRefresh / 无 auto-measure LM）
     * 的 Page 在 `WRAP_CONTENT` + `AT_MOST` 链路下塌成 0 高度（典型坑：
     * StaggeredGridLayoutManager.isAutoMeasureEnabled() = false）。
     */
    private fun defaultLayoutParams(target: ViewGroup): ViewGroup.LayoutParams = when (target) {
        is LinearLayout -> LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        else -> ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
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

