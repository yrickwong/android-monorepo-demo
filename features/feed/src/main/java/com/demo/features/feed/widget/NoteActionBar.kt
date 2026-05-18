package com.demo.features.feed.widget

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.demo.features.feed.FeedShellViewModelKey
import com.demo.features.feed.R
import com.demo.features.feed.databinding.FeedWidgetNoteActionBarBinding
import com.demo.foundations.analytics.Analytics
import com.demo.foundations.assemblekit.requirePageContext

/**
 * "Like / Share" action bar. Lives at depth 2 inside a row:
 *
 * ```
 * <ListPage row root>          ← PageContext stamped here by BinderAdapter
 *   <LinearLayout> (vertical)
 *     <TextView>title
 *     ...
 *     <NoteActionBar>          ← this view
 *       <Button>Like
 *       <Button>Share
 * ```
 *
 * Why this widget is the canonical "view-tree lookup" sample:
 *  - It is **reusable** across screens. Hard-coding `FeedShellViewModel`
 *    in the constructor would make it Feed-only; resolving the VM at
 *    click time via [requirePageContext] keeps the widget bound only to
 *    the *contract* (the key) and not the host.
 *  - It takes **one piece of data through `bind`**: the note id. Every
 *    behavioural dependency (the VM, the analytics tracker) comes from
 *    the view tree — proving you don't need to drill constructor
 *    parameters or callback lambdas through binders/holders to reach a
 *    custom view two layers deep.
 *  - It calls `requirePageContext()` lazily, *inside* the click
 *    listener, not in `init {}`. Reason: when AppCompat / XML inflates
 *    the view, the parent chain is not yet attached, so `parent` is
 *    null. By the time the user can click, the row has been attached
 *    to a stamped ancestor and the lookup is cheap (O(depth), 4-5
 *    hops worst case).
 *
 * Behaviour:
 *  - The Like click calls `vm.likeOne(noteId)` — the same shared state
 *    that drives the row's "♥ N" label. Tapping here visibly bumps the
 *    counter on the same row because the list is a derived projection
 *    of `FeedShellState.notes`.
 *  - The Share click calls `vm.share(noteId)`, which mutates
 *    `FeedShellState.lastShared`. The footer Page subscribes to that
 *    field, so a single click 2 layers deep in this widget surfaces in
 *    a Page rendered at a *different* mount slot — exactly the
 *    cross-page coordination the Shell VM is there to make trivial.
 */
class NoteActionBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: FeedWidgetNoteActionBarBinding

    /**
     * The single piece of "data" we accept from outside. Reset on every
     * `bind()` because RecyclerView recycles rows; if the value were
     * left stale a click on a recycled row would like the wrong note.
     */
    private var noteId: String? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        // For <merge> layouts the generated `inflate` already attaches
        // to the parent — there is no `attachToParent` overload.
        binding = FeedWidgetNoteActionBarBinding.inflate(
            LayoutInflater.from(context),
            this,
        )

        binding.actionLike.setOnClickListener {
            val id = noteId ?: return@setOnClickListener
            Analytics.logEvent("feed_action_like", mapOf("note" to id))
            // The whole point: a reusable widget reaches the *current*
            // host's Shell VM via the view tree, not via constructor
            // injection. Swap this widget into another AssembleKit
            // page that provides `FeedShellViewModelKey` and it works
            // unchanged — that is what the convention buys us.
            requirePageContext().requireConsume(FeedShellViewModelKey).likeOne(id)
        }
        binding.actionShare.setOnClickListener {
            val id = noteId ?: return@setOnClickListener
            Analytics.logEvent("feed_action_share", mapOf("note" to id))
            requirePageContext().requireConsume(FeedShellViewModelKey).share(id)
        }
    }

    /**
     * The one-and-only public contract. Reusable widgets should
     * accept *data*, not *behaviour*. Behaviour comes from the
     * environment (the PageContext).
     */
    fun bind(noteId: String) {
        this.noteId = noteId
        binding.actionLike.contentDescription =
            context.getString(R.string.feed_action_like_cd, noteId)
        binding.actionShare.contentDescription =
            context.getString(R.string.feed_action_share_cd, noteId)
    }
}
