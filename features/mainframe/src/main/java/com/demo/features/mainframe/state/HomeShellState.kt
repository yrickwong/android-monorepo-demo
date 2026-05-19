package com.demo.features.mainframe.state

import com.airbnb.mvrx.Async
import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.Uninitialized
import com.demo.features.mainframe.data.MockRepository

/**
 * Home Pane 的 Shell State——整个 Home（CENTER 槽位）的唯一可观测真相源。
 *
 * 设计要点：
 *  - [feed] 用 Mavericks `Async<T>`，把"加载中 / 成功 / 失败"显式建模，Pages 通过
 *    `onAsync(HomeShellState::feed)` 声明式订阅。
 *  - [tabs] / [selectedTabIndex] 是原始数据；UI 真正消费的是派生的 [tabRows]——
 *    把"显示文本 + 是否选中"打包成一个稳定的 data class，让 RecyclerView
 *    的 DiffUtil 能精确识别哪一行变了（而不是整列重绘）。
 *  - [bottomBarCollapsed] 是"结构性 UI 状态"：Feed 上滑 → true → 底部栏收起。它跟随
 *    [com.demo.foundations.slidepane.gesture.ScrollDirectionListener] 的事件被回写。
 *    放在 state 里而不是 BottomBarPage 的本地变量，是为了 config change 后能恢复。
 *  - [feedList] 是派生属性：消费方不必到处写 `feed() ?: emptyList()`。
 */
data class HomeShellState(
    val tabs: List<String> = emptyList(),
    val selectedTabIndex: Int = 1,
    val feed: Async<List<MockRepository.FeedNote>> = Uninitialized,
    val refreshing: Boolean = false,
    val bottomBarCollapsed: Boolean = false,
) : MavericksState {
    val feedList: List<MockRepository.FeedNote> get() = feed() ?: emptyList()

    /**
     * Tab 渲染用的派生列表：HomeTabBinder 直接消费它。把 selected 状态打包进
     * item 而不是单独维护一个"selectedIndex"，这样：
     *   1) DiffUtil 能基于 selected 字段变化精确触发 areContentsTheSame，避免整列重绘；
     *   2) ItemBinder 的 bind() 是 stateless 的（不必读外部 selectedIndex 变量）。
     */
    val tabRows: List<HomeTabRow>
        get() = tabs.mapIndexed { idx, name -> HomeTabRow(idx, name, idx == selectedTabIndex) }
}

/**
 * 顶部分类 Tab 在 RecyclerView 中的渲染模型。
 *
 * @param index 在 [HomeShellState.tabs] 中的位置；点击时通过它通知 VM。
 * @param name  显示文本。
 * @param selected 是否高亮选中——绑定到 TextView.isSelected 走 selector。
 */
data class HomeTabRow(
    val index: Int,
    val name: String,
    val selected: Boolean,
)
