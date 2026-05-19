package com.demo.features.mainframe.pages.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.demo.features.mainframe.R
import com.demo.features.mainframe.data.MockRepository
import com.demo.features.mainframe.pages.home.binders.HomeNoteBinder
import com.demo.features.mainframe.state.HomeShellState
import com.demo.features.mainframe.state.HomeShellViewModelKey
import com.demo.foundations.assemblekit.PageContext
import com.demo.foundations.assemblekit.ViewPage
import com.demo.foundations.assemblekit.setPageContext

/**
 * 首页瀑布流 Feed（CENTER 槽位的核心区域）。
 *
 * 不使用框架的 [com.demo.foundations.assemblekit.list.ListPage]——因为本页需要：
 *  1) 用 [SwipeRefreshLayout] 包住 RecyclerView 提供下拉刷新；
 *  2) 用 [StaggeredGridLayoutManager] 做 2 列瀑布流；
 *  3) 动态测列宽，再传给 [HomeNoteBinder] 用于按 aspectRatio 算卡片高度。
 *
 * 这些是 ListPage 因为"自建 RV / final 生命周期"无法满足的需求——文档第 5 节
 * 把这种场景列为"ListPage 不适用，回退到 ViewPage 自建"，这里就是那个例子。
 *
 * 状态来源：从 PageContext 拿 [HomeShellViewModelKey]：
 *  - `onEach(HomeShellState::feedList)` → adapter.submitList
 *  - `onEach(HomeShellState::refreshing)` → SwipeRefresh isRefreshing
 *  - SwipeRefresh onRefresh → vm.refresh()
 */
internal class HomeFeedPage : ViewPage() {

    private var swipeRefresh: SwipeRefreshLayout? = null
    private var recyclerView: RecyclerView? = null
    private val noteBinder = HomeNoteBinder()
    private val adapter = FeedAdapter(noteBinder)

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View {
        val root = inflater.inflate(R.layout.mainframe_page_home_feed, parent, false)
        swipeRefresh = root.findViewById(R.id.mainframe_home_swipe_refresh)
        recyclerView = root.findViewById(R.id.mainframe_home_rv_feed)
        return root
    }

    override fun onViewCreated(view: View) {
        val rv = recyclerView ?: return
        val refresh = swipeRefresh ?: return
        val viewModel = requireConsume(HomeShellViewModelKey)

        // 把当前 PageContext 注入到 binder——让 binder 能在 createView 时给每行 view
        // 烙印 ParentPageContext（虽然 ListPage 帮忙做这事，但我们没用 ListPage，
        // 所以要在 adapter 自己 onCreateViewHolder 里手动做）。
        adapter.parentCtx = context

        rv.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL).apply {
            gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_NONE
        }
        rv.setHasFixedSize(false)
        rv.adapter = adapter

        refresh.setColorSchemeResources(R.color.mainframe_red_primary)
        refresh.setOnRefreshListener { viewModel.refresh() }

        // 1) Feed 列表更新
        viewModel.onEach(HomeShellState::feedList) { items ->
            adapter.submit(items)
        }

        // 2) 下拉刷新状态
        viewModel.onEach(HomeShellState::refreshing) { isRefreshing ->
            if (refresh.isRefreshing != isRefreshing) {
                refresh.isRefreshing = isRefreshing
            }
        }

        // 3) 量列宽——首屏一次性测好后给 binder，notifyItemRangeChanged 触发重绘
        rv.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val width = rv.width
                if (width <= 0) return
                rv.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val density = view.resources.displayMetrics.density
                val columnWidth = (width / 2) - (2 * density).toInt() * 2
                noteBinder.setColumnWidth(columnWidth)
                if (adapter.itemCount > 0) {
                    adapter.notifyItemRangeChanged(0, adapter.itemCount)
                }
            }
        })
    }

    override fun onDestroyView() {
        recyclerView?.adapter = null
        recyclerView = null
        swipeRefresh = null
    }

    // ------------------------------------------------------------------
    // 内部 Adapter——很薄，只是把 HomeNoteBinder 桥接到 RecyclerView，
    // 并在 onCreateViewHolder 时给每行 view stamp PageContext，复用框架
    // ListPage 的"view-tree 透传"约定。
    // ------------------------------------------------------------------

    private class FeedAdapter(
        private val binder: HomeNoteBinder,
    ) : RecyclerView.Adapter<RowHolder>() {

        var parentCtx: PageContext? = null
        private val data: MutableList<MockRepository.FeedNote> = mutableListOf()

        fun submit(items: List<MockRepository.FeedNote>) {
            data.clear()
            data.addAll(items)
            @Suppress("NotifyDataSetChanged")
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
            val ctx = parentCtx
                ?: error("HomeFeedPage adapter.parentCtx was not set before createView")
            val view = binder.createView(parent, ctx)
            view.setPageContext(ctx)
            return RowHolder(view)
        }

        override fun onBindViewHolder(holder: RowHolder, position: Int) {
            val ctx = parentCtx ?: return
            binder.bind(holder.itemView, data[position], position, ctx)
        }

        override fun onViewRecycled(holder: RowHolder) {
            binder.unbind(holder.itemView)
        }

        override fun getItemCount(): Int = data.size
    }

    private class RowHolder(view: View) : RecyclerView.ViewHolder(view)
}
