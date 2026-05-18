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
    val container: ViewGroup,
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

    private val assemblyId: String =
        "asm-${host.hostId}-${container.id.takeIf { it != View.NO_ID } ?: container.hashCode()}"

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

    private val pages = mutableListOf<Page>()
    private val attached = mutableListOf<Page>()
    private var installed = false

    /** Read-only snapshot of attached pages, in declaration order. */
    val pagesSnapshot: List<Page> get() = attached.toList()

    internal fun add(page: Page) {
        check(!installed) { "Cannot add Page to an already-installed Assembly. Use replace { ... }." }
        pages += page
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

        pages.forEachIndexed { index, page ->
            attachPage(page, index)
        }
    }

    private fun attachPage(page: Page, index: Int) {
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
            val view = page.performAttach(ctx, container)
            container.addView(view, defaultLayoutParams())
            attached += page
            Logger.d(LOG_TAG, "[$assemblyId] attached page #$index id=$pageId")
        } catch (t: Throwable) {
            Logger.e(LOG_TAG, "[$assemblyId] failed to attach page #$index: ${t.message}")
            throw t
        }
    }

    private fun derivePageId(page: Page, index: Int): String =
        "${host.hostId}::${page.javaClass.simpleName}#$index"

    private fun defaultLayoutParams(): ViewGroup.LayoutParams = when (container) {
        is LinearLayout -> LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        else -> ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    companion object {
        private const val LOG_TAG = "Assembly"
    }
}

