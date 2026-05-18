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
 * A single, lightweight UI slice that knows how to materialise itself,
 * hook up a Mavericks ViewModel, and talk to its siblings via scoped buses.
 *
 * `Page` is intentionally **abstract over the rendering mechanism** — the
 * actual "produce a View" contract lives on the concrete subtypes:
 *
 *  - [ViewPage]        — classic XML / inflated View layer (default today)
 *  - [ComposablePage]  — Jetpack Compose payload (stub; ships in
 *    `:foundations:assemblekit-compose` once the team is ready)
 *
 * Everything below — lifecycle, savedstate, buses, Mavericks ViewModel
 * delegate, host bridging — is identical for both flavours.
 *
 * Why not just use a Fragment?
 *  - Pages don't go on the back stack. There is no FragmentManager, no
 *    transactions, no commit-now-or-later distinction.
 *  - Pages don't double-bookkeep state. Configuration changes are
 *    handled entirely through Mavericks (`@PersistState` etc.).
 *  - Pages can be constructed directly with `new` in unit tests; you
 *    only need a fake [PageContext] to exercise the wiring.
 *
 * Lifecycle model:
 *
 *  ```
 *  attach(ctx)     -> Lifecycle.State.CREATED
 *  materialize     -> ... (ViewPage.onCreateView / ComposablePage.Content)
 *  hostStart       -> STARTED
 *  hostResume      -> RESUMED
 *  hostPause       -> STARTED
 *  hostStop        -> CREATED
 *  detach          -> DESTROYED   (pageScope cancelled, buses unreachable)
 *  ```
 */
abstract class Page(
    /**
     * Optional explicit id. If null, an id is auto-generated from the
     * class name plus the assembly slot index. Provide an explicit id
     * when you need a stable Mavericks ViewModel across config changes
     * even when the page order changes.
     */
    private val explicitId: String? = null,
) : LifecycleOwner, SavedStateRegistryOwner, MavericksView {

    // ------------------------------------------------------------------
    // Lifecycle / SavedState
    // ------------------------------------------------------------------

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    // ------------------------------------------------------------------
    // Context / identity
    // ------------------------------------------------------------------

    /**
     * The environment object handed in by the framework at [performAttach].
     * Accessing it before attach throws — by design: subclasses should
     * not assume an environment until [materialize] runs.
     */
    protected lateinit var context: PageContext
        private set

    /** Stable id, unique within the parent [Assembly]. */
    val pageId: String get() = if (::context.isInitialized) context.pageId else fallbackId()

    /**
     * Friend-style accessor for framework-internal helpers (e.g. the
     * `pageViewModel()` delegate) that need to reach the host without
     * exposing the `protected context` to outside callers.
     */
    @PublishedApi
    internal fun hostOrNullInternal(): PageHost? =
        if (::context.isInitialized) context.host else null

    /** Mavericks uses this to scope state subscriptions. */
    final override val mvrxViewId: String get() = pageId

    /**
     * We deliberately *do not* use Mavericks' `invalidate()` pattern.
     * All state subscriptions should be expressed with `onEach` /
     * `onAsync` selectors, which are both more precise and more
     * efficient (no full re-render for unrelated state changes).
     */
    final override fun invalidate(): Unit = Unit

    private fun fallbackId(): String = explicitId ?: "${javaClass.simpleName}@${hashCode()}"

    // ------------------------------------------------------------------
    // Convenience scopes / buses (only valid after attach)
    // ------------------------------------------------------------------

    protected val pageScope: CoroutineScope     get() = context.pageScope
    protected val assemblyScope: CoroutineScope get() = context.assemblyScope
    protected val hostScope: CoroutineScope     get() = context.hostScope

    // ------------------------------------------------------------------
    // Subclass extension points
    // ------------------------------------------------------------------

    /**
     * Produce the root [View] for this Page. Called once, between
     * `ON_CREATE` and the host's first `ON_START` event.
     *
     * Concrete subtypes implement this:
     *  - [ViewPage] inflates XML and runs `onCreateView` + `onViewCreated`
     *  - [ComposablePage] wraps a `Content()` composable in a `ComposeView`
     *
     * The returned View is added to the assembly container by the framework;
     * subclasses must **not** add it themselves.
     */
    internal abstract fun materialize(inflater: LayoutInflater, parent: ViewGroup): View

    /**
     * Generic teardown hook. Called after the View is detached and right
     * before [Lifecycle.State.DESTROYED]. [ViewPage] funnels its own
     * `onDestroyView` through here; subclasses with other resources
     * (Compose disposables, native handles, …) can override directly.
     */
    protected open fun onDestroy(): Unit = Unit

    // ------------------------------------------------------------------
    // Event / command helpers
    // ------------------------------------------------------------------

    /** Listen on the page's own bus (intra-page events). Auto-cancelled on detach. */
    protected inline fun <reified E : Any> onPageEvent(
        noinline block: suspend (E) -> Unit,
    ): Job = context.pageBus.on(pageScope, block)

    /** Listen on the parent [Assembly]'s bus (sibling-page events). */
    protected inline fun <reified E : Any> onAssemblyEvent(
        noinline block: suspend (E) -> Unit,
    ): Job = context.assemblyBus.on(pageScope, block)

    /** Listen on the host's bus (Activity/Fragment-wide events). */
    protected inline fun <reified E : Any> onHostEvent(
        noinline block: suspend (E) -> Unit,
    ): Job = context.hostBus.on(pageScope, block)

    /** Publish to siblings inside the same Assembly. */
    protected fun emitToAssembly(event: Any): Boolean = context.assemblyBus.emit(event)

    /** Publish to the host (Activity/Fragment-wide). */
    protected fun emitToHost(event: Any): Boolean = context.hostBus.emit(event)

    // ------------------------------------------------------------------
    // Scoped locals (provides / consume)
    // ------------------------------------------------------------------

    /**
     * Resolve a value provided anywhere in the page→assembly→host chain.
     * Returns `null` if no scope provides this key. Equivalent to
     * `context.consume(key)`; the inline-friendly shortcut lives here
     * so subclasses don't need to reach into `context`.
     */
    protected fun <T> consume(key: PageContextKey<T>): T? = context.consume(key)

    /** Like [consume] but throws if the key was never provided. */
    protected fun <T> requireConsume(key: PageContextKey<T>): T = context.requireConsume(key)

    /**
     * Provide a value visible **only to this Page** (and to any nested
     * consumers it explicitly hands its [PageContext] to, e.g. items in
     * a `ListPage`). Shadowing the parent for one page is a common need
     * — e.g. a "preview" Page wanting to use a fake repository while
     * sibling Pages still see the real one.
     *
     * For framework-wide or screen-wide provides, do it from the
     * `assemble {}` DSL or from the host's `hostLocal[...] = …` setter.
     */
    protected fun <T> providesPage(key: PageContextKey<T>, value: T) {
        context.pageLocal[key] = value
    }

    // ------------------------------------------------------------------
    // Framework-only entry points (called by Assembly)
    // ------------------------------------------------------------------

    internal var view: View? = null
        private set

    /**
     * Reference to the host-lifecycle observer registered in [bridgeHostLifecycle],
     * kept so [performDetach] can unregister it. Without this we'd leak one
     * observer per page every time [Assembly.replace] swaps the composition.
     */
    private var hostObserver: LifecycleEventObserver? = null

    internal fun performAttach(ctx: PageContext, parent: ViewGroup): View {
        require(!::context.isInitialized) { "Page $pageId already attached" }
        context = ctx

        // SavedStateRegistry must be restored before any consumer reads it.
        savedStateController.performRestore(null /* savedInstanceState handled by host */)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        val inflater = LayoutInflater.from(parent.context)
        val created = materialize(inflater, parent)
        view = created

        // Mirror host's current lifecycle state — if the host is already
        // STARTED/RESUMED when the assembly is built, we catch up
        // synchronously so subscribers see consistent events.
        bridgeHostLifecycle(ctx.host)

        return created
    }

    internal fun performDetach() {
        try {
            onDestroy()
        } catch (t: Throwable) {
            Logger.w(LOG_TAG, "onDestroy threw for $pageId: ${t.message}")
        }
        view = null

        // Unsubscribe from host's lifecycle so we don't pile up observers
        // across Assembly.replace cycles. Guarded because performDetach
        // also runs when the host itself is being destroyed, at which
        // point the observer list is already being torn down.
        hostObserver?.let { obs ->
            if (::context.isInitialized) {
                try {
                    context.host.lifecycle.removeObserver(obs)
                } catch (_: Throwable) {
                    /* host already gone, fine */
                }
            }
        }
        hostObserver = null

        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }

    /**
     * Forward host's lifecycle events into our own [lifecycleRegistry] so
     * Mavericks subscriptions / coroutines tied to [pageScope] receive
     * consistent state transitions.
     *
     * We listen on the *host* (not the assembly) so that pages added to a
     * later assembly still get correct STARTED/RESUMED events even if the
     * host was already RESUMED at the time the assembly was built.
     */
    private fun bridgeHostLifecycle(host: PageHost) {
        // Catch up first…
        val hostState = host.lifecycle.currentState
        if (hostState.isAtLeast(Lifecycle.State.STARTED) &&
            lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.CREATED)
        ) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }
        if (hostState.isAtLeast(Lifecycle.State.RESUMED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        // …then track future transitions. Save the observer reference
        // so performDetach can unregister it.
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
