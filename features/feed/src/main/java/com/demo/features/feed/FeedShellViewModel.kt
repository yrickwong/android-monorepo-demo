package com.demo.features.feed

import com.airbnb.mvrx.MavericksViewModel
import com.airbnb.mvrx.MavericksViewModelFactory
import com.airbnb.mvrx.Success
import com.airbnb.mvrx.ViewModelContext
import com.demo.features.feed.data.FeedRepository

/**
 * Shell ViewModel: owns all observable Feed state and is the only
 * thing in the module allowed to call `setState`.
 *
 * Lifecycle: created once per [FeedActivity] (Activity ViewModel scope),
 * survives configuration changes. The Pages do NOT own this VM; they
 * `consume(FeedShellViewModelKey)` from the Assembly's locals and call
 * methods on it.
 *
 * Why Activity-scoped instead of Page-scoped?
 *  - Multiple Pages render slices of the same shell data (list, footer,
 *    banner-toggle). Page-scoped VMs would force them to coordinate via
 *    buses or to duplicate state.
 *  - `Assembly.replace { }` tears down Pages but must not throw away
 *    user-visible state. Anchoring the VM to the host's ViewModelStore
 *    makes "swap Pages, keep state" the default behaviour.
 */
internal class FeedShellViewModel(
    initialState: FeedShellState,
    private val repository: FeedRepository,
) : MavericksViewModel<FeedShellState>(initialState) {

    init {
        // Kick off the first load eagerly. Loading state is visible to
        // every subscriber via `onAsync(FeedShellState::notes)`.
        refresh()
    }

    /**
     * Re-fetch the list. Wrapped in `execute {}` so Mavericks drives
     * the `Async` lifecycle (Uninitialized → Loading → Success/Fail)
     * for us — Pages then bind to that lifecycle declaratively.
     */
    fun refresh() {
        suspend { repository.load() }.execute { copy(notes = it) }
    }

    /**
     * Like a single note. Synchronous in the demo, so we just compute
     * the new list and `setState`. If this ever talks to the network,
     * convert to `suspend { repository.like(id) }.execute { ... }` and
     * the UI will get the in-flight state for free.
     */
    fun likeOne(noteId: String) = setState {
        // Operate on the cached snapshot inside the Async, not on the
        // repository's mutable view: keeps the state strictly derived
        // from what's currently visible to subscribers.
        val newList = repository.like(noteId)
        copy(notes = Success(newList))
    }

    /** Toggle the decorative banner. Drives `Assembly.replace { }` from the Activity. */
    fun toggleBanner() = setState { copy(showBanner = !showBanner) }

    /**
     * Mavericks' canonical recipe for injecting non-state constructor
     * params. The framework calls this once per ViewModelStore and
     * caches the result, so we get a fresh [FeedRepository] per host
     * (Activity scope) without any DI container.
     *
     * If we later wire a real DI graph, replace the `FeedRepository()`
     * line with `viewModelContext.activity.di.feedRepository` — the
     * call sites in [FeedActivity] do not change.
     */
    companion object : MavericksViewModelFactory<FeedShellViewModel, FeedShellState> {
        override fun create(
            viewModelContext: ViewModelContext,
            state: FeedShellState,
        ): FeedShellViewModel = FeedShellViewModel(state, FeedRepository())
    }
}
