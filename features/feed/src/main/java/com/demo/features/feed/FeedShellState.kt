package com.demo.features.feed

import com.airbnb.mvrx.Async
import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.Uninitialized
import com.demo.features.feed.model.Note

/**
 * Single source of truth for the Feed screen.
 *
 * Everything observable on the screen — the note list (with its
 * loading / failure cycle) and structural toggles like
 * [showBanner] — lives here. Pages subscribe to slices via
 * `viewModel.onEach(FeedShellState::field)` / `onAsync(...)`; nobody
 * holds a parallel `StateFlow`, nobody passes a list through
 * constructor parameters.
 *
 * Why one state object for the whole shell rather than one per Page?
 *  - The Activity drives `assembly.replace { }` from this same state
 *    ([showBanner]). Having shell-level structural concerns live next
 *    to the data they decorate keeps the "what should be on screen"
 *    decision in one place.
 *  - Adding a new Page that needs to know note-count or whether the
 *    user is in a "refreshing" state costs zero plumbing — it just
 *    `consume`s the same VM and subscribes to its own slice.
 *  - If a Page eventually has private state that nobody else cares
 *    about (e.g. local editor draft), THAT is when it should grow its
 *    own `pageViewModel<MyDraftViewModel>()`. Default: shared.
 */
internal data class FeedShellState(
    val notes: Async<List<Note>> = Uninitialized,
    val showBanner: Boolean = false,
    /**
     * Last note id that received a `share` click from `NoteActionBar`.
     * Nullable because nothing has been shared yet on a fresh launch.
     * The footer Page renders this so a human reviewing the demo can
     * eyeball that a click 3 layers deep actually mutated shell state.
     */
    val lastShared: String? = null,
    /**
     * Last tag a user tapped inside `RelatedTagsCarousel`'s inner
     * RecyclerView. Same role as [lastShared] but for the even-deeper
     * (4 layers) ViewHolder case. Kept here, not in a Page-local VM,
     * because the footer summary needs to see it too — proof that
     * `findPageContext()` correctly resolves the *shared* VM and not
     * some accidental row-scoped instance.
     */
    val lastTag: String? = null,
) : MavericksState {

    /**
     * Convenience derived property so consumers don't repeat
     * `notes() ?: emptyList()` everywhere. Pure derivation, never
     * persisted, recomputed on demand.
     */
    val noteList: List<Note> get() = notes() ?: emptyList()
}
