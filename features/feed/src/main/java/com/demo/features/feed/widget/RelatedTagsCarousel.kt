package com.demo.features.feed.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.demo.features.feed.FeedShellViewModelKey
import com.demo.features.feed.R
import com.demo.foundations.analytics.Analytics
import com.demo.foundations.assemblekit.findPageContext

/**
 * Horizontal "related tags" carousel — a custom view that hosts its
 * own internal [RecyclerView] of tag chips. Lives at depth 2 inside
 * a row; each chip's ViewHolder is therefore at depth 4 from the row
 * root and depth 5 from the host frame:
 *
 * ```
 * <host frame>                          (no tag here)
 *   <ListPage RecyclerView>             (no tag here)
 *     <row root>                        ← PageContext stamped (depth 1)
 *       <LinearLayout vertical>         (depth 2)
 *         <RelatedTagsCarousel>         (depth 3)
 *           <RecyclerView>              (depth 4)
 *             <chip itemView>           (depth 5)
 * ```
 *
 * The chip's click listener reaches the Shell VM with **one line** —
 * `view.findPageContext()?.requireConsume(FeedShellViewModelKey)?.bumpTag(tag)`.
 * There is no callback drilled through `RelatedTagsCarousel.setOnTagClick`
 * → `BinderAdapter.bindTagClick` → `NoteItemBinder.tagListener`. The
 * inner adapter is genuinely self-contained.
 *
 * Why [findPageContext] (nullable) instead of `requirePageContext`:
 *  - The same `feed_widget_tag_chip` layout might be rendered in a
 *    preview / snapshot tool that has no Page wrapper. Nullable
 *    resolution lets us silently no-op there instead of crashing the
 *    preview.
 *  - The action bar, in contrast, is mandatory production UI — it
 *    uses `requirePageContext` so misuse fails loudly with a pointer
 *    at the missing setup.
 */
class RelatedTagsCarousel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val recycler = RecyclerView(context).apply {
        layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        // We never expect more than a dozen tags per note; turning
        // animations off keeps the row crisp when DiffUtil swaps items
        // during a refresh. The point of the demo is the wiring, not
        // animation polish.
        itemAnimator = null
        overScrollMode = OVER_SCROLL_NEVER
    }

    private val adapter = TagAdapter()

    init {
        addView(
            recycler,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
        recycler.adapter = adapter
    }

    /**
     * Public contract: a single data setter. No callbacks, no
     * VM-typed parameters. The carousel does not know — and must not
     * know — what host it is mounted in; the chips will find their
     * Shell VM through the view tree at click time.
     */
    fun submit(tags: List<String>) {
        adapter.submitList(tags)
    }

    /**
     * Inner adapter intentionally kept private — there is no reason
     * for callers to subclass or replace it. If we ever need to
     * customise chip rendering, prefer adding fields to the data
     * (e.g. a `TagChip(label, badge)`) over exposing the adapter.
     */
    private class TagAdapter : ListAdapter<String, TagViewHolder>(TagDiff) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.feed_widget_tag_chip, parent, false) as TextView
            return TagViewHolder(view)
        }

        override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    private class TagViewHolder(itemView: TextView) : RecyclerView.ViewHolder(itemView) {
        private val label: TextView = itemView

        fun bind(tag: String) {
            label.text = "#$tag"
            label.setOnClickListener { v ->
                Analytics.logEvent("feed_tag_click", mapOf("tag" to tag))
                // 4 layers deep, one line: this is the whole point of
                // the view-tree lookup. No callback was drilled through
                // RelatedTagsCarousel, no listener was set from the
                // binder, the adapter does not even know what host it
                // is mounted in.
                v.findPageContext()
                    ?.requireConsume(FeedShellViewModelKey)
                    ?.bumpTag(tag)
            }
        }
    }

    private object TagDiff : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(old: String, new: String): Boolean = old == new
        override fun areContentsTheSame(old: String, new: String): Boolean = old == new
    }
}
