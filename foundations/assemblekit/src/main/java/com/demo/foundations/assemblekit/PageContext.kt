package com.demo.foundations.assemblekit

import androidx.lifecycle.ViewModelStoreOwner
import com.demo.foundations.assemblekit.bus.ScopedCommandBus
import com.demo.foundations.assemblekit.bus.ScopedEventBus
import com.demo.foundations.assemblekit.local.ScopedContainer
import kotlinx.coroutines.CoroutineScope

/**
 * The "environment object" injected into every [Page] by the framework.
 *
 * Carrying these references inside a single value (instead of making
 * `Page` implement a dozen interfaces) keeps `Page` subclasses focused
 * on UI logic and makes them trivially mockable in unit tests — you
 * can construct a [PageContext] with fakes and assert what your Page
 * publishes / subscribes to without spinning up an Activity.
 *
 * Three scopes are exposed at increasing breadth:
 *
 *  - [pageScope]     → cancelled when the Page itself is destroyed
 *  - [assemblyScope] → cancelled when the parent [Assembly] is destroyed
 *  - [hostScope]     → cancelled when the [PageHost] is destroyed
 *
 * Each scope has its own [ScopedEventBus] / [ScopedCommandBus]. Pick
 * the smallest scope that satisfies the use case — see
 * `docs/assemblekit.md` for the decision matrix.
 *
 * **Identity rules:**
 *  - [pageId] is unique within the parent [Assembly]. It is used as
 *    the Mavericks view-id and as the ViewModel key so the right
 *    state is restored after configuration changes.
 */
class PageContext internal constructor(
    val host: PageHost,
    val assembly: Assembly,
    val pageId: String,

    val pageScope: CoroutineScope,
    val assemblyScope: CoroutineScope,
    val hostScope: CoroutineScope,

    val pageBus: ScopedEventBus,
    val assemblyBus: ScopedEventBus,
    val hostBus: ScopedEventBus,

    val pageCommands: ScopedCommandBus,
    val assemblyCommands: ScopedCommandBus,
    val hostCommands: ScopedCommandBus,

    /**
     * Scoped "locals" containers, à la React Context / Compose
     * CompositionLocal. Each layer has its own [ScopedContainer], chained
     * together as page → assembly → host: a [pageLocal] lookup that
     * misses falls through to assembly, then host.
     *
     * Use [consume] (or the `Page.consume(key)` shortcut) for the
     * "give me whatever the framework provided" case — that's the path
     * 99% of code should take. Direct access to a specific layer
     * (`pageLocal[key] = …`) is reserved for advanced overrides such as
     * "this single item wants to shadow what the assembly provided".
     */
    val pageLocal: ScopedContainer,
    val assemblyLocal: ScopedContainer,
    val hostLocal: ScopedContainer,

    /**
     * Use this owner when calling Mavericks' `existingViewModel()` /
     * `activityViewModel()` to share state between Pages of the same host.
     * It points at the host's ViewModelStore, **not** the per-Page one.
     */
    val hostViewModelStoreOwner: ViewModelStoreOwner,
) {
    /**
     * Resolve [key] by walking page → assembly → host. Returns the first
     * provider, or `null` if no scope provides this key.
     *
     * For required dependencies prefer [requireConsume] so the error
     * message points the caller at the missing `provides` call site.
     */
    fun <T> consume(key: com.demo.foundations.assemblekit.local.PageContextKey<T>): T? =
        pageLocal.resolve(key)

    /** Like [consume] but throws if the key was never provided. */
    fun <T> requireConsume(key: com.demo.foundations.assemblekit.local.PageContextKey<T>): T =
        pageLocal.require(key)
}
