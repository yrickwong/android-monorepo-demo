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
import com.demo.foundations.assemblekit.setPageContext
import com.demo.thirdparty.logger.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * A heterogeneous version of [ListPage]: each item in [itemsFlow] can
 * be a different subtype of the common supertype [T], rendered by a
 * different [ItemBinder] picked up at runtime from a class-based
 * registry.
 *
 * ## Why a separate Page (and not "just a flag on ListPage")
 *
 * The single-type [ListPage] is a 95% case. Forcing every list to opt
 * into a per-item dispatch table would (a) clutter the simple call
 * site with a one-entry builder, (b) push every binder through an
 * unnecessary `Class.isInstance` check on the hot bind path, and
 * (c) blur the "all rows in this list are equivalent" reading of the
 * code. The two Pages share zero public surface beyond what RecyclerView
 * itself imposes; keeping them separate is cheaper than parameterising
 * one of them into doing both jobs.
 *
 * Existing [ItemBinder] implementations work as-is — there is no new
 * interface to implement. [MultiTypeListPage] is a strictly additive
 * Page type; [ListPage] is unchanged.
 *
 * ## Usage
 *
 * ```kotlin
 * sealed interface FeedRow {
 *     data class NoteRow(val note: Note) : FeedRow
 *     data class AdRow(val ad: Ad)       : FeedRow
 *     data object LoadingRow             : FeedRow
 * }
 *
 * class NoteRowBinder    : ItemBinder<FeedRow.NoteRow>    { ... }
 * class AdRowBinder      : ItemBinder<FeedRow.AdRow>      { ... }
 * class LoadingRowBinder : ItemBinder<FeedRow.LoadingRow> { ... }
 *
 * // in assemble { }:
 * +MultiTypeListPage(
 *     itemsFlow = vm.stateFlow.map { it.feedRows }.distinctUntilChanged(),
 * ) {
 *     bind<FeedRow.NoteRow>(NoteRowBinder())
 *     bind<FeedRow.AdRow>(AdRowBinder())
 *     bind<FeedRow.LoadingRow>(LoadingRowBinder())
 * } at R.id.feed_body_slot
 * ```
 *
 * ## Dispatch rules
 *
 *  - Item → binder lookup uses `Class.isInstance` against the type
 *    declared at `bind<T>()`, **in registration order**. Register
 *    more-specific types first if you have a `sealed` hierarchy
 *    where a row could conceivably match more than one entry.
 *  - An item whose class matches no registered binder is a programmer
 *    error: it throws an [IllegalStateException] at the next bind
 *    cycle with the offending class name. Use an explicit
 *    "loading" / "error" row type instead of allowing `null` or
 *    "anything else" semantics.
 *  - DiffUtil treats rows of different types as different items even
 *    if `==` would say otherwise; that prevents the framework from
 *    trying to rebind an `AdRow` view as a `NoteRow`.
 *
 * ## What carries over from [ListPage]
 *
 *  - Each row's `itemView` is stamped with the parent Page's
 *    [PageContext] in `onCreateViewHolder`, so deep descendants can
 *    resolve via [com.demo.foundations.assemblekit.findPageContext].
 *  - `itemsFlow` is collected via `collectLatest` on [pageScope];
 *    detach / [com.demo.foundations.assemblekit.Assembly.replace]
 *    cancels the collector and drops the adapter reference.
 *  - `itemsFlow` should be derived from the Shell VM's
 *    `stateFlow.map { ... }.distinctUntilChanged()`; do not feed a
 *    hot repository flow directly — same MVI rule as [ListPage].
 */
open class MultiTypeListPage<T : Any>(
    private val itemsFlow: Flow<List<T>>,
    binders: MultiTypeBindersBuilder<T>.() -> Unit,
    private val layoutManagerFactory: (parent: ViewGroup) -> RecyclerView.LayoutManager = { parent ->
        LinearLayoutManager(parent.context)
    },
    explicitId: String? = null,
) : ViewPage(explicitId = explicitId) {

    private val entries: List<TypedBinderEntry<T, out T>> =
        MultiTypeBindersBuilder<T>().apply(binders).entries.toList().also {
            require(it.isNotEmpty()) {
                "MultiTypeListPage requires at least one bind<T>() entry"
            }
        }

    private var recyclerView: RecyclerView? = null
    private var adapter: MultiBinderAdapter<T>? = null

    final override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View {
        // Same care as ListPage: capture the parent Page's PageContext
        // explicitly. Inside the `apply` block `context` would resolve
        // to View.getContext() (an Android Context).
        val pageCtx: PageContext = context
        val rv = RecyclerView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            layoutManager = layoutManagerFactory(parent)
            adapter = MultiBinderAdapter(entries, pageCtx).also { this@MultiTypeListPage.adapter = it }
        }
        recyclerView = rv
        return rv
    }

    final override fun onViewCreated(view: View) {
        val adapter = adapter ?: return
        pageScope.launch {
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
        recyclerView?.adapter = null
        recyclerView = null
        adapter = null
    }

    // ---- adapter / diff -----------------------------------------------

    private class MultiBinderAdapter<T : Any>(
        private val entries: List<TypedBinderEntry<T, out T>>,
        private val parentCtx: PageContext,
    ) : ListAdapter<T, BinderViewHolder>(MultiDiffCallback(entries)) {

        override fun getItemViewType(position: Int): Int {
            val item = getItem(position)
            return indexOfBinderFor(item, entries)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BinderViewHolder {
            @Suppress("UNCHECKED_CAST")
            val binder = entries[viewType].binder as ItemBinder<Any?>
            val rowView = binder.createView(parent, parentCtx)
            // Stamp the row root so descendants can reach this Page's
            // PageContext via view.findPageContext() — mirrors ListPage.
            rowView.setPageContext(parentCtx)
            return BinderViewHolder(rowView)
        }

        override fun onBindViewHolder(holder: BinderViewHolder, position: Int) {
            val item = getItem(position)
            val idx = indexOfBinderFor(item, entries)
            @Suppress("UNCHECKED_CAST")
            val binder = entries[idx].binder as ItemBinder<Any?>
            binder.bind(holder.itemView, item, position, parentCtx)
        }

        override fun onViewRecycled(holder: BinderViewHolder) {
            // We don't know which binder a recycled holder belongs to
            // without re-deriving from position, which the framework
            // doesn't give us at recycle time. Fall back to a no-op;
            // binders that need an explicit unbind hook can subclass
            // and override onBindViewHolder + maintain a sidecar map
            // keyed by holder identity. For the common case (text /
            // image / click listener), no action is needed.
        }
    }

    private class BinderViewHolder(view: View) : RecyclerView.ViewHolder(view)

    private class MultiDiffCallback<T : Any>(
        private val entries: List<TypedBinderEntry<T, out T>>,
    ) : DiffUtil.ItemCallback<T>() {

        override fun areItemsTheSame(old: T, new: T): Boolean {
            val oldIdx = indexOfBinderForOrNull(old, entries)
            val newIdx = indexOfBinderForOrNull(new, entries)
            // Different row types are never "the same item" — prevents
            // RecyclerView from trying to rebind a NoteRow holder as
            // an AdRow.
            if (oldIdx == null || newIdx == null || oldIdx != newIdx) return false
            @Suppress("UNCHECKED_CAST")
            val binder = entries[oldIdx].binder as ItemBinder<Any?>
            return binder.areItemsTheSame(old, new)
        }

        override fun areContentsTheSame(old: T, new: T): Boolean {
            val idx = indexOfBinderForOrNull(old, entries) ?: return false
            if (indexOfBinderForOrNull(new, entries) != idx) return false
            @Suppress("UNCHECKED_CAST")
            val binder = entries[idx].binder as ItemBinder<Any?>
            return binder.areContentsTheSame(old, new)
        }
    }

    companion object {
        private const val LOG_TAG = "MultiTypeListPage"

        private fun <T : Any> indexOfBinderFor(
            item: T,
            entries: List<TypedBinderEntry<T, out T>>,
        ): Int {
            val idx = indexOfBinderForOrNull(item, entries)
            checkNotNull(idx) {
                "MultiTypeListPage: no ItemBinder registered for item of type " +
                    "${item.javaClass.name}. Registered: ${entries.joinToString { it.type.name }}"
            }
            return idx
        }

        private fun <T : Any> indexOfBinderForOrNull(
            item: T,
            entries: List<TypedBinderEntry<T, out T>>,
        ): Int? {
            for (i in entries.indices) {
                if (entries[i].type.isInstance(item)) return i
            }
            return null
        }
    }
}

/**
 * DSL builder collected by [MultiTypeListPage]'s constructor. Use
 * [bind] to register one [ItemBinder] per concrete subtype of the
 * list's common element type.
 *
 * Registration order matters when a row could match multiple entries
 * — earlier `bind<T>()` calls win. Register the most specific type
 * first.
 */
class MultiTypeBindersBuilder<T : Any> @PublishedApi internal constructor() {

    @PublishedApi
    internal val entries: MutableList<TypedBinderEntry<T, out T>> = mutableListOf()

    /**
     * Register [binder] as the renderer for all items whose runtime
     * class is assignable to [C].
     */
    inline fun <reified C : T> bind(binder: ItemBinder<C>) {
        entries += TypedBinderEntry(C::class.java, binder)
    }
}

/**
 * Internal pairing of "concrete row type" + "binder that knows how to
 * render it". Public only because the inline `bind<C>()` extension on
 * [MultiTypeBindersBuilder] needs to construct it from product code.
 */
class TypedBinderEntry<T : Any, C : T> @PublishedApi internal constructor(
    internal val type: Class<C>,
    internal val binder: ItemBinder<C>,
)
