package com.demo.features.feed.binder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.demo.features.feed.FeedShellViewModelKey
import com.demo.features.feed.databinding.FeedItemNoteBinding
import com.demo.features.feed.model.Note
import com.demo.foundations.analytics.Analytics
import com.demo.foundations.assemblekit.PageContext
import com.demo.foundations.assemblekit.list.ItemBinder

/**
 * Renders a single note row.
 *
 * Showcase for "VM in locals":
 *  - The binder grabs the shell ViewModel from the parent Page's
 *    `PageContext` via `ctx.requireConsume(FeedShellViewModelKey)`.
 *  - The tap handler is now `viewModel.likeOne(item.id)` — one call
 *    site, one source of truth, exactly the path AGENTS.md Rule 2
 *    sketches for "interact with shared state". No `NoteClickKey`
 *    indirection, no lambda passed in through 3 layers.
 *
 * The binder itself stays an `object` (zero state). Reuse across rows
 * and across `submitList` cycles is fine because it never closes over
 * any per-row data.
 */
internal object NoteItemBinder : ItemBinder<Note> {

    override fun createView(parent: ViewGroup, ctx: PageContext): View {
        val inflater = LayoutInflater.from(parent.context)
        return FeedItemNoteBinding.inflate(inflater, parent, false).root
    }

    override fun bind(view: View, item: Note, position: Int, ctx: PageContext) {
        val b = FeedItemNoteBinding.bind(view)
        b.noteTitle.text = item.title
        b.noteAuthor.text = "by ${item.author}"
        b.noteLikes.text = "♥ ${item.likes}"

        // Pull the shell VM out of the assembly-scoped locals.
        // `requireConsume` throws with a pointer to the missing
        // `provides(...)` call site if someone wires this Binder into
        // an Assembly that forgot to expose the key — much better
        // failure mode than a null callback at click time.
        val viewModel = ctx.requireConsume(FeedShellViewModelKey)
        view.setOnClickListener {
            Analytics.logEvent("feed_item_click", mapOf("note" to item.id))
            viewModel.likeOne(item.id)
        }
    }

    override fun unbind(view: View) {
        // Drop the click listener so a recycled view doesn't trigger the
        // old item's click during the brief window before re-binding.
        view.setOnClickListener(null)
    }

    override fun areItemsTheSame(old: Note, new: Note): Boolean = old.id == new.id
    // data-class equality already covers "all visible fields match",
    // so the default areContentsTheSame is fine.
}
