package com.demo.foundations.assemblekit.list

import android.view.View
import android.view.ViewGroup
import com.demo.foundations.assemblekit.PageContext

/**
 * Renderer contract for a single row inside a [ListPage].
 *
 * `ItemBinder<T>` is intentionally **not** another [com.demo.foundations.assemblekit.Page]:
 * a thousand-row feed should not pay for a thousand lifecycle owners,
 * a thousand Mavericks ViewModels or a thousand scoped event buses.
 *
 * Instead, rows are "context-transparent": they share the [PageContext]
 * of their parent [ListPage], which means they can reach anything the
 * outer screen has provided — repositories, click bridges, theme tokens —
 * via [PageContext.consume] / [PageContext.requireConsume] without the
 * caller having to drill parameters down through every callsite.
 *
 * Typical usage:
 * ```kotlin
 * val NoteRepoKey = pageContextKey<NoteRepository>("note-repo")
 *
 * class NoteItemBinder : ItemBinder<Note> {
 *     override fun createView(parent: ViewGroup, ctx: PageContext): View =
 *         FeedItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false).root
 *
 *     override fun bind(view: View, item: Note, position: Int, ctx: PageContext) {
 *         val repo = ctx.requireConsume(NoteRepoKey)
 *         view.findViewById<TextView>(R.id.title).text = item.title
 *         view.setOnClickListener { repo.markRead(item.id) }
 *     }
 * }
 * ```
 *
 * Implementations are expected to be **stateless** — any per-row state
 * belongs in [T] itself or in the repository surfaced through the context.
 * The framework will reuse a single [ItemBinder] instance across many
 * positions and across [ListPage] re-binds; do not cache view references
 * on the binder.
 */
interface ItemBinder<T> {

    /**
     * Inflate / build the row [View]. Called once per RecyclerView
     * viewHolder; the framework caches the result.
     *
     * Do not attach the view to [parent]; the framework will wrap it in
     * a ViewHolder and let RecyclerView manage attachment.
     */
    fun createView(parent: ViewGroup, ctx: PageContext): View

    /**
     * Bind [item] data into [view]. Called whenever the row is shown or
     * the underlying item changes. [position] is the row's adapter
     * position at bind time — do not cache it.
     */
    fun bind(view: View, item: T, position: Int, ctx: PageContext)

    /**
     * Optional teardown when the row leaves the screen permanently
     * (RecyclerView's `onViewRecycled`). Default no-op covers the
     * common case where binders only set text / images.
     */
    fun unbind(view: View) = Unit

    /**
     * DiffUtil hook: are these the *same logical row*? Defaults to
     * `==` which is correct when [T] has a stable identity (e.g. a
     * data class keyed by id). Override for "two snapshots of the same
     * entity" semantics.
     */
    fun areItemsTheSame(old: T, new: T): Boolean = old == new

    /**
     * DiffUtil hook: do these two snapshots render identically? Defaults
     * to `==`. Override when [T] carries fields the row does not display
     * and you want to skip needless re-binds.
     */
    fun areContentsTheSame(old: T, new: T): Boolean = old == new
}
