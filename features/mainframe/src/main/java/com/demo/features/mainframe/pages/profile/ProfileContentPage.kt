package com.demo.features.mainframe.pages.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.demo.features.mainframe.data.MockRepository
import com.demo.features.mainframe.pages.home.binders.HomeNoteBinder
import com.demo.features.mainframe.pages.profile.binders.ProfileHeaderBinder
import com.demo.features.mainframe.state.ProfileShellState
import com.demo.features.mainframe.state.ProfileShellViewModelKey
import com.demo.foundations.assemblekit.PageContext
import com.demo.foundations.assemblekit.ViewPage
import com.demo.foundations.assemblekit.setPageContext

/**
 * 个人页主体：Header + 用户笔记瀑布流（START 槽位的核心区域）。
 *
 * 不使用框架的 [com.demo.foundations.assemblekit.list.MultiTypeListPage]——原因和
 * [com.demo.features.mainframe.pages.home.HomeFeedPage] 完全一致：
 *  1) 需要 [StaggeredGridLayoutManager] 的 isFullSpan 支持，要在
 *     `onViewAttachedToWindow` 里设置（MultiTypeListPage 的 adapter 是内部类，
 *     无法 hook 这个回调）；
 *  2) 需要在首次 layout 时测列宽，再传给 [HomeNoteBinder]——MultiTypeListPage
 *     自己 new RecyclerView 不暴露给外部测量；
 *  3) `MultiTypeListPage.onCreateView/onViewCreated/onDestroyView` 都是 final，
 *     即使继承也插不进自定义逻辑。
 *
 * 状态来源：从 PageContext 拿 [ProfileShellViewModelKey]，订阅
 * `ProfileShellState::profile`（Async 包裹），Success 时把 Profile 拆成
 * Header + Notes 两类喂给 adapter。
 *
 * Note binder 直接复用 Home 的 [HomeNoteBinder]——两边卡片样式完全一致，
 * 没必要复制一份。
 */
internal class ProfileContentPage : ViewPage() {

    private var recyclerView: RecyclerView? = null
    private val headerBinder = ProfileHeaderBinder()
    private val noteBinder = HomeNoteBinder()
    private val adapter = ProfileAdapter(headerBinder, noteBinder)

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View {
        // Profile 的 slot_content 直接就是 RV 的位置——没有 SwipeRefresh 包裹，
        // 所以这里干脆程序化创建 RV，省去一个 layout 文件。
        val rv = RecyclerView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            clipToPadding = false
        }
        recyclerView = rv
        return rv
    }

    override fun onViewCreated(view: View) {
        val rv = recyclerView ?: return
        val viewModel = requireConsume(ProfileShellViewModelKey)

        adapter.parentCtx = context

        rv.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL).apply {
            gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_NONE
        }
        rv.adapter = adapter

        // 1) 订阅 ProfileShellState.profile（Async<UserProfile>）
        //    Success 才更新 adapter——Loading/Fail 当前 mock 不会触发，留空。
        viewModel.onEach(ProfileShellState::profile) { async ->
            val profile = async.invoke() ?: return@onEach
            adapter.submit(profile)
        }

        // 2) 量列宽 → 给 noteBinder；和 HomeFeedPage 一样的算法（width/2 - 2*density*2）
        rv.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val width = rv.width
                if (width <= 0) return
                rv.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val density = view.resources.displayMetrics.density
                val columnWidth = (width / 2) - (2 * density).toInt() * 2
                noteBinder.setColumnWidth(columnWidth)
                if (adapter.itemCount > 1) {
                    adapter.notifyItemRangeChanged(1, adapter.itemCount - 1)
                }
            }
        })
    }

    override fun onDestroyView() {
        recyclerView?.adapter = null
        recyclerView = null
    }

    // ------------------------------------------------------------------
    // 内部 Adapter：position 0 = Header，position 1.. = Note。
    // 复用框架 ListPage 的"view-tree 透传"约定，在 onCreateViewHolder 里给
    // 每行 view 烙印 ParentPageContext。
    // ------------------------------------------------------------------

    private class ProfileAdapter(
        private val headerBinder: ProfileHeaderBinder,
        private val noteBinder: HomeNoteBinder,
    ) : RecyclerView.Adapter<RowHolder>() {

        var parentCtx: PageContext? = null
        private var profile: MockRepository.UserProfile? = null

        fun submit(p: MockRepository.UserProfile) {
            val oldNoteCount = profile?.notes?.size ?: 0
            profile = p
            // 数据集结构稳定（Header 永远在 0，Notes 在 1..），刷一次足够；
            // mock 数据初次加载只会触发一次，无需用 DiffUtil 做细粒度计算。
            @Suppress("NotifyDataSetChanged")
            if (oldNoteCount == 0) notifyDataSetChanged() else notifyItemRangeChanged(0, itemCount)
        }

        override fun getItemViewType(position: Int): Int =
            if (position == 0) TYPE_HEADER else TYPE_NOTE

        override fun getItemCount(): Int = profile?.let { 1 + it.notes.size } ?: 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
            val ctx = parentCtx
                ?: error("ProfileContentPage adapter.parentCtx was not set before createView")
            val view = when (viewType) {
                TYPE_HEADER -> headerBinder.createView(parent, ctx)
                else -> noteBinder.createView(parent, ctx)
            }
            view.setPageContext(ctx)
            return RowHolder(view)
        }

        override fun onBindViewHolder(holder: RowHolder, position: Int) {
            val ctx = parentCtx ?: return
            val p = profile ?: return
            if (position == 0) {
                headerBinder.bind(holder.itemView, p, position, ctx)
            } else {
                noteBinder.bind(holder.itemView, p.notes[position - 1], position, ctx)
            }
        }

        /**
         * Header 单项需要占满两列。和原 ProfileAdapter 一样，在 attach 时设置
         * isFullSpan——此时 layoutParams 已被 StaggeredGridLayoutManager 包装成
         * 它的专用类型，cast 不会失败。
         */
        override fun onViewAttachedToWindow(holder: RowHolder) {
            super.onViewAttachedToWindow(holder)
            if (holder.bindingAdapterPosition == 0) {
                val lp = holder.itemView.layoutParams
                if (lp is StaggeredGridLayoutManager.LayoutParams) {
                    lp.isFullSpan = true
                }
            }
        }

        override fun onViewRecycled(holder: RowHolder) {
            // header/note 复用判断：bindingAdapterPosition 此时可能已失效，
            // 走 view 内部状态清理即可——两个 binder 当前都没有副作用。
            noteBinder.unbind(holder.itemView)
        }

        companion object {
            private const val TYPE_HEADER = 0
            private const val TYPE_NOTE = 1
        }
    }

    private class RowHolder(view: View) : RecyclerView.ViewHolder(view)
}
