package com.demo.features.feed.binder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.demo.features.feed.NoteClickKey
import com.demo.features.feed.databinding.FeedItemNoteBinding
import com.demo.features.feed.model.Note
import com.demo.foundations.assemblekit.PageContext
import com.demo.foundations.assemblekit.list.ItemBinder

/**
 * Showcase for "context transparency": the row needs both a way to
 * render itself *and* a way to call back into the host's click
 * handler. Instead of taking either as a constructor parameter, it
 * reaches into the parent Page's [PageContext] and pulls them out
 * with `requireConsume`.
 *
 * The binder itself is a `object` (zero state). The same instance is
 * reused for every row and every `submitList` cycle.
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

        // Pull the click handler out of the assembly-scoped locals.
        // `requireConsume` throws with a pointer to the missing
        // `provides(...)` site if someone wires this Binder into an
        // Assembly that forgot to expose the key — a much better
        // failure mode than a null callback at click time.
        val onClick = ctx.requireConsume(NoteClickKey)
        view.setOnClickListener { onClick(item) }
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
