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
 * 一个 [ViewPage]，底层用一个 [ItemBinder] + 一个 RecyclerView，渲染一个同构的
 * [T] 列表。
 *
 * 为什么把这个放在框架里（而不是 "你在自己的 Page 里直接用 RecyclerView"）：
 *
 *  - **Context 透明性**。binder 拿到的是*父 Page 的* [PageContext]，
 *    所以外层 `assemble { }` 里 `provides(...)` 的任何东西，每个 row 都能通过
 *    `ctx.consume(key)` 直接看到。不用再把 repository 塞进 Page 构造函数、
 *    塞进 Adapter、再塞进 ViewHolder。
 *  - **Lifecycle 卫生**。row 的订阅和 itemsFlow 的 collector 都挂在 [pageScope] 上；
 *    detach（包括 [com.demo.foundations.assemblekit.Assembly.replace]）时一次性
 *    全部取消——不会有 observer 跨 recomposition 泄漏。
 *  - **没有嵌套 Page**。每个 row 都只是一个普通 [View]，不是 Page。一个 1000 行的 feed
 *    不会分配出 1000 个 lifecycle owner / ViewModel / scoped bus。
 *
 * 例子：
 * ```kotlin
 * class NotesListPage(
 *     itemsFlow: Flow<List<Note>>,
 * ) : ListPage<Note>(itemsFlow = itemsFlow, itemBinder = NoteItemBinder())
 *
 * // 在 assemble { } 里：
 * provides(NoteRepoKey, repo)
 * +NotesListPage(repo.notes)
 * ```
 *
 * 异构列表（"这里是 text card，那里是 image card"）在 v2 里故意没纳入范围——
 * 要么把两个 `ListPage` 拼起来，要么等后面的 `MultiTypeListPage`。
 *
 * @param itemsFlow 列表的响应式数据源。每次 emit 都会通过 [ItemBinder.areItemsTheSame] /
 *   [ItemBinder.areContentsTheSame] 与上一次 diff。collector 挂在 [pageScope] 上，
 *   并使用 `collectLatest`，所以慢的上游不会把帧堆积起来。
 * @param itemBinder 每个 row 如何渲染。见 [ItemBinder]。
 * @param layoutManagerFactory 如果需要 GridLayoutManager、横向滚动等请覆写。
 *   默认是纵向 [LinearLayoutManager]。
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
        // 把父 Page 的 PageContext 在 `apply` 块外面捕获——
        // 进了 `apply` 块以后，`context` 解析成的是 View.getContext()（一个 Android Context），
        // 那不是我们想交给 ItemBinder 的东西。
        val pageCtx: PageContext = context
        val rv = RecyclerView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            layoutManager = layoutManagerFactory(parent)
            // 共享一个稳定的 adapter，这样 DiffUtil 才能跨多次 emit 正常工作。
            adapter = BinderAdapter(itemBinder, pageCtx).also { this@ListPage.adapter = it }
        }
        recyclerView = rv
        return rv
    }

    final override fun onViewCreated(view: View) {
        val adapter = adapter ?: return
        pageScope.launch {
            // collectLatest：如果上一次的 DiffUtil 还在算，新的 list 就到了，
            // 直接把旧的那帧丢掉。屏幕上显示的永远是最新的那一次 emit。
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
        // 在 RecyclerView 本身被丢之前先把 adapter 引用清掉，避免在
        // Assembly.replace 之间把 items 列表 / context 一直钉在内存里。
        recyclerView?.adapter = null
        recyclerView = null
        adapter = null
    }

    private class BinderAdapter<T>(
        private val binder: ItemBinder<T>,
        private val parentCtx: PageContext,
    ) : ListAdapter<T, BinderViewHolder>(DiffCallback(binder)) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BinderViewHolder {
            val rowView = binder.createView(parent, parentCtx)
            // 给 row 根节点打上父 Page 的 PageContext tag。
            // 正是这个魔法，能让 row 内部嵌套了三层的自定义 View——
            // 或者 row 内层 RecyclerView 的 ViewHolder——调一下
            // `view.findPageContext()`，就能拿到与 binder 看到的同一个
            // Shell VM，全程不用一层层往下传参数。
            rowView.setPageContext(parentCtx)
            return BinderViewHolder(rowView)
        }

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
