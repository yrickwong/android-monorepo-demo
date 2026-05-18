@file:OptIn(com.airbnb.mvrx.InternalMavericksApi::class)

package com.demo.foundations.assemblekit

import androidx.appcompat.app.AppCompatActivity
import com.airbnb.mvrx.ActivityViewModelContext
import com.airbnb.mvrx.InternalMavericksApi
import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.MavericksViewModel
import com.airbnb.mvrx.MavericksViewModelProvider

/**
 * Convenience lazy delegate that hands a [Page] a Mavericks ViewModel
 * scoped to the **host Activity's** ViewModelStore (so configuration
 * changes restore it for free), but keyed by `pageId::ViewModelClass`
 * so each Page sees its own instance.
 *
 * Why store the VM on the host rather than the Page itself?
 *  - Pages do not survive configuration changes (the framework
 *    re-builds them via `assemble {}`), but their ViewModels must.
 *  - Mavericks' built-in machinery for `@PersistState` and SavedState
 *    integration assumes an `Activity` / `Fragment` ViewModelContext —
 *    delegating to the host keeps us inside that supported envelope.
 *
 * Trade-off: when an [Assembly] is `replace()`d at runtime, the
 * detached Page's ViewModel stays in the host store until the host
 * itself is destroyed. For the typical case (assembly lives for the
 * full Activity lifetime) this is fine. A future iteration can expose
 * `Assembly.clearViewModels()` to evict them eagerly.
 *
 * Usage:
 * ```kotlin
 * class LoginBodyPage : Page() {
 *     private val viewModel: LoginBodyViewModel by pageViewModel()
 *     // ...
 * }
 * ```
 */
inline fun <reified VM : MavericksViewModel<S>, reified S : MavericksState> Page.pageViewModel(
    /** Override the storage key. Defaults to `{pageId}::{VM class name}`. */
    noinline keyFactory: () -> String = { "$pageId::${VM::class.java.name}" },
): Lazy<VM> = lazy(LazyThreadSafetyMode.NONE) {
    val activity = activityOrNull(this)
        ?: error(
            "pageViewModel() requires the host to be an AppCompatActivity " +
                "(got ${pageHostOrNull(this)?.javaClass?.name}). " +
                "If you're hosting Pages inside a Fragment, switch to " +
                "fragmentPageViewModel() — coming in a follow-up iteration.",
        )

    MavericksViewModelProvider.get(
        viewModelClass = VM::class.java,
        stateClass = S::class.java,
        viewModelContext = ActivityViewModelContext(activity = activity, args = null),
        key = keyFactory(),
    )
}

// ---- internal helpers (kept here to avoid leaking `context` accessor) ----

@PublishedApi
internal fun pageHostOrNull(page: Page): PageHost? = try {
    // Reflective-free fast path: PageContext is package-private to the framework
    // and the `context` property is `protected`; we expose hostOrNull via a
    // friend-style trampoline on Page itself.
    page.hostOrNullInternal()
} catch (t: Throwable) {
    null
}

@PublishedApi
internal fun activityOrNull(page: Page): AppCompatActivity? =
    pageHostOrNull(page) as? AppCompatActivity
