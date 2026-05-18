package com.demo.foundations.assemblekit

import androidx.lifecycle.ViewModelStoreOwner
import com.demo.foundations.assemblekit.bus.ScopedCommandBus
import com.demo.foundations.assemblekit.bus.ScopedEventBus
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
     * Use this owner when calling Mavericks' `existingViewModel()` /
     * `activityViewModel()` to share state between Pages of the same host.
     * It points at the host's ViewModelStore, **not** the per-Page one.
     */
    val hostViewModelStoreOwner: ViewModelStoreOwner,
)
