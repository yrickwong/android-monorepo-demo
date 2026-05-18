package com.demo.foundations.assemblekit

import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.savedstate.SavedStateRegistryOwner
import com.demo.foundations.assemblekit.bus.ScopedCommandBus
import com.demo.foundations.assemblekit.bus.ScopedEventBus
import com.demo.foundations.assemblekit.local.ScopedContainer

/**
 * The container of an [Assembly] — typically an `Activity`, a `Fragment`,
 * or a `Dialog`. A host owns the *outermost* scope visible to every Page
 * it hosts:
 *
 *  - [hostBus]      — broadcast events scoped to this host
 *  - [hostCommands] — request/response channel scoped to this host
 *  - lifecycle / ViewModelStore / SavedStateRegistry — used by Pages to
 *    create their own [PageScope]s underneath this host
 *
 * Host scope is **strictly bigger** than Assembly scope (an Activity can
 * contain several Assemblies — e.g. a main content area + a bottom-sheet —
 * and all of them share the same host bus).
 *
 * Implementations are provided as [PageHostActivity] / [PageHostFragment];
 * if you need a custom host (say, a Compose-based one) implement this
 * interface directly.
 */
interface PageHost : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    /** Stable identifier for diagnostics / Mavericks view ids. */
    val hostId: String

    /** Host-wide broadcast bus. Lives as long as the host. */
    val hostBus: ScopedEventBus

    /** Host-wide request/response bus. Lives as long as the host. */
    val hostCommands: ScopedCommandBus

    /**
     * Host-wide "locals" container. Anything you put here is visible to
     * every Page hosted underneath, via [PageContext.consume] / the
     * `Page.consume(key)` helper.
     *
     * Typical use: stash a long-lived dependency the host already owns
     * (an `AppEnv`, a logged-in session, a router) so child Pages don't
     * have to take it through constructor parameters.
     *
     * ```kotlin
     * class FeedActivity : PageHostActivity() {
     *     override fun onCreate(s: Bundle?) {
     *         super.onCreate(s)
     *         hostLocal[FeedRepositoryKey] = FeedRepository.real()
     *         assemble(host = this) { +HeaderPage(); +FeedListPage() }
     *     }
     * }
     * ```
     */
    val hostLocal: ScopedContainer

    /**
     * Resolve a [ViewGroup] by id within this host's view tree. Used by
     * the `at(R.id.…)` DSL to pin individual Pages onto specific slots
     * inside the host's `setContentView()` layout.
     *
     * Returns `null` if the id is not present (or the host hasn't called
     * `setContentView` yet, in which case `assemble {}` was called too
     * early — fix the call site, don't make this swallow).
     */
    fun findContainer(@IdRes id: Int): ViewGroup?
}

/**
 * Convenience base class for Activity hosts. Pair with `assemble {}`:
 *
 * ```kotlin
 * class LoginActivity : PageHostActivity() {
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         val root = FrameLayout(this).also { setContentView(it) }
 *         assemble(container = root) {
 *             +LoginHeaderPage()
 *             +LoginBodyPage()
 *             +LoginBottomPage()
 *         }
 *     }
 * }
 * ```
 *
 * `MavericksAppCompatActivity` would be a natural alternative once
 * Mavericks is wired in app-wide; for now we keep the dependency tree
 * small and only require `AppCompatActivity`. Subclasses can swap
 * the parent class freely — the [PageHost] interface is what matters.
 */
abstract class PageHostActivity : AppCompatActivity(), PageHost {

    override val hostId: String by lazy { "act-${javaClass.simpleName}-${hashCode()}" }

    override val hostBus: ScopedEventBus by lazy {
        ScopedEventBus(tag = "HostBus($hostId)")
    }

    override val hostCommands: ScopedCommandBus by lazy {
        ScopedCommandBus(tag = "HostCmd($hostId)")
    }

    override val hostLocal: ScopedContainer by lazy {
        ScopedContainer.root(debugName = "hostLocal($hostId)")
    }

    override fun findContainer(@IdRes id: Int): ViewGroup? = findViewById(id)

    // Activity already implements ViewModelStoreOwner, LifecycleOwner and
    // SavedStateRegistryOwner; nothing else to wire here.
}

/**
 * Convenience base class for Fragment hosts. Behaviour mirrors
 * [PageHostActivity]; useful when the page assembly lives inside a
 * fragment (e.g. inside a ViewPager2 tab).
 */
abstract class PageHostFragment : Fragment, PageHost {

    constructor() : super()
    constructor(contentLayoutId: Int) : super(contentLayoutId)

    override val hostId: String by lazy { "frg-${javaClass.simpleName}-${hashCode()}" }

    override val hostBus: ScopedEventBus by lazy {
        ScopedEventBus(tag = "HostBus($hostId)")
    }

    override val hostCommands: ScopedCommandBus by lazy {
        ScopedCommandBus(tag = "HostCmd($hostId)")
    }

    override val hostLocal: ScopedContainer by lazy {
        ScopedContainer.root(debugName = "hostLocal($hostId)")
    }

    override fun findContainer(@IdRes id: Int): ViewGroup? = view?.findViewById(id)

    /** Convenience accessor matching Activity's `lifecycleScope` syntax. */
    @Suppress("unused")
    protected val hostScope get() = lifecycleScope
}
