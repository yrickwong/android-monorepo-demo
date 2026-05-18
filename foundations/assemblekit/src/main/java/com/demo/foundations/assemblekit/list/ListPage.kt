package com.demo.foundations.assemblekit.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.demo.foundations.assemblekit.PageContext
import com.demo.foundations.assemblekit.ViewPage
import com.demo.thirdparty.logger.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * A [ViewPage] that renders a homogeneous list of [T] using a single
 * [ItemBinder] and a RecyclerView under the hood.
 *
 * Why this lives in the framework (instead of "just use RecyclerView
 * in your own Page"):
 *
 *  - **Context transparency**. The binder receives the *parent Page's*
 *    [PageContext], so any `provides(...)` from the surrounding
 *    `assemble { }` is visible to every row via `ctx.consume(key)`.
 *    No more passing a repository into a Page constructor just to pass
 *    it into an Adapter just to pass it into a ViewHolder.
 *  - **Lifecycle hygiene**. Item subscriptions and the items-flow
 *    collector are tied to [pageScope]; on detach (including
 *    [com.demo.foundations.assemblekit.Assembly.replace]) everything
 *    is cancelled in one shot — no observers leak across recompositions.
 *  - **No nested Pages**. Each row is a plain [View], not a Page. A
 *    1000-row feed does not allocate 1000 lifecycle owners, ViewModels
 *    or scoped buses.
 *
 * Example:
 * ```kotlin
 * class NotesListPage(
 *     itemsFlow: Flow<List<Note>>,
 * ) : ListPage<Note>(itemsFlow = itemsFlow, itemBinder = NoteItemBinder())
 *
 * // in assemble { }:
 * provides(NoteRepoKey, repo)
 * +NotesListPage(repo.notes)
 * ```
 *
 * Heterogeneous lists ("text card here, image card there") are
 * intentionally out of scope for v2 — compose two `ListPage` instances,
 * or wait for a future `MultiTypeListPage`.
 *
 * @param itemsFlow Reactive source of the list. Each emission is diffed
 *   against the previous one via [ItemBinder.areItemsTheSame] /
 *   [ItemBinder.areContentsTheSame]. The collector lives on [pageScope]
 *   and uses `collectLatest`, so a slow upstream cannot pile up frames.
 * @param itemBinder How to render each row. See [ItemBinder].
 * @param layoutManagerFactory Override if you need GridLayoutManager,
 *   horizontal scrolling, etc. Default: vertical [LinearLayoutManager].
 */
open class ListPage<T>(
    private val itemsFlow: Flow<List<T>>,
    private val itemBinder: ItemBinder<T>,
    private val layoutManagerFactory: (parent: ViewGroup) -> RecyclerView.LayoutManager = { parent ->
        LinearLayoutManager(parent.context)
    },
    explicitId: String? = null,
) : ViewPage(explicitId = explicitId) {

    private var recyclerView: RecyclerView? = null
    private var adapter: BinderAdapter<T>? = null

    final override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View {
        // Capture the parent Page's PageContext outside the `apply` block —
        // inside it, `context` resolves to View.getContext() (an Android Context),
        // which is not what we want to hand to the ItemBinder.
        val pageCtx: PageContext = context
        val rv = RecyclerView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            layoutManager = layoutManagerFactory(parent)
            // Stable shared adapter so DiffUtil can do its job across emissions.
            adapter = BinderAdapter(itemBinder, pageCtx).also { this@ListPage.adapter = it }
        }
        recyclerView = rv
        return rv
    }

    final override fun onViewCreated(view: View) {
        val adapter = adapter ?: return
        pageScope.launch {
            // collectLatest: if a new list arrives while DiffUtil is still
            // crunching the previous one, drop the older frame. The visible
            // truth always reflects the most recent emission.
            itemsFlow.collectLatest { items ->
                try {
                    adapter.submitList(items)
                } catch (t: Throwable) {
                    Logger.w(LOG_TAG, "submitList failed: ${t.message}")
                }
            }
        }
    }

    final override fun onDestroyView() {
        // Drop the adapter reference before the RecyclerView itself is
        // discarded so we don't keep the items list / context pinned in
        // memory between Assembly.replace cycles.
        recyclerView?.adapter = null
        recyclerView = null
        adapter = null
    }

    private class BinderAdapter<T>(
        private val binder: ItemBinder<T>,
        private val parentCtx: PageContext,
    ) : ListAdapter<T, BinderViewHolder>(DiffCallback(binder)) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BinderViewHolder =
            BinderViewHolder(binder.createView(parent, parentCtx))

        override fun onBindViewHolder(holder: BinderViewHolder, position: Int) {
            @Suppress("UNCHECKED_CAST")
            (binder as ItemBinder<Any?>).bind(holder.itemView, getItem(position), position, parentCtx)
        }

        override fun onViewRecycled(holder: BinderViewHolder) {
            binder.unbind(holder.itemView)
        }
    }

    private class BinderViewHolder(view: View) : RecyclerView.ViewHolder(view)

    private class DiffCallback<T>(
        private val binder: ItemBinder<T>,
    ) : DiffUtil.ItemCallback<T>() {
        override fun areItemsTheSame(old: T & Any, new: T & Any): Boolean =
            binder.areItemsTheSame(old, new)

        override fun areContentsTheSame(old: T & Any, new: T & Any): Boolean =
            binder.areContentsTheSame(old, new)
    }

    companion object {
        private const val LOG_TAG = "ListPage"
    }
}
