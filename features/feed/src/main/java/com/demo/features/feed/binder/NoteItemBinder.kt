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
 * What this binder is **responsible for**:
 *  - Inflate the row layout.
 *  - Push raw data onto each child: title / author / likes / note id
 *    onto the action bar / tag list onto the carousel.
 *  - Wire the row-level "like by tapping the body" affordance (a
 *    common shorthand that lives outside the action bar).
 *
 * What this binder is explicitly **not** responsible for:
 *  - Drilling the Shell ViewModel into [com.demo.features.feed.widget.NoteActionBar]
 *    or [com.demo.features.feed.widget.RelatedTagsCarousel]. Those
 *    widgets resolve the VM via `view.requirePageContext()` /
 *    `view.findPageContext()` themselves. That is the design we are
 *    showcasing here — try, as a thought experiment, to add a
 *    `vm: FeedShellViewModel` parameter to `NoteActionBar`'s
 *    constructor. XML inflation breaks (XML can't pass non-`Context/
 *    AttributeSet` params); you would add a `setViewModel(vm)`
 *    setter; the binder would grow a `NoteActionBar.setViewModel(...)`
 *    call; the carousel would grow a `setViewModel(vm)` call that has
 *    to fan it out to every inner ViewHolder. That is the
 *    parameter-drill death spiral the view-tree lookup is designed to
 *    avoid.
 *
 * The binder still consumes the VM itself — but only for the row-body
 * click handler, which is its own behaviour. The widgets don't piggy-
 * back on this consumption; they each look the VM up independently
 * when they need it.
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

        // Hand each reusable widget *data only*. The widgets resolve
        // any behavioural dependency from the view-tree PageContext.
        b.noteActions.bind(item.id)
        b.noteTags.submit(item.tags)

        // The row body's own click — kept here, not in the action bar,
        // because tapping the title is a binder-level shorthand for
        // "like", distinct from the action bar's explicit Like button.
        val viewModel = ctx.requireConsume(FeedShellViewModelKey)
        view.setOnClickListener {
            Analytics.logEvent("feed_item_click", mapOf("note" to item.id))
            viewModel.likeOne(item.id)
        }
    }

    override fun unbind(view: View) {
        // Drop the click listener so a recycled view doesn't trigger
        // the old item's click during the brief window before re-binding.
        view.setOnClickListener(null)
    }

    override fun areItemsTheSame(old: Note, new: Note): Boolean = old.id == new.id
    // data-class equality already covers "all visible fields match",
    // so the default areContentsTheSame is fine.
}
