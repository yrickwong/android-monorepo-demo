package com.demo.features.mainframe.state

import com.airbnb.mvrx.Fail
import com.airbnb.mvrx.MavericksViewModel
import com.airbnb.mvrx.Success
import com.demo.features.mainframe.data.MockRepository

/**
 * Home Pane 的 Shell ViewModel。
 *
 * 生命周期：随 [com.demo.features.mainframe.pages.home.HomePaneHostFragment] 创建（Fragment ViewModel scope），
 * 跨 config change 保活，跟 Fragment 一起销毁。
 *
 * 所有 Page（TopBar / Tabs / Feed / BottomBar）通过 `requireConsume(HomeShellViewModelKey)`
 * 拿到本 VM 实例。Pages 不持有任何 `MutableStateFlow / var`——这是 AGENTS.md Rule 2 的硬要求。
 */
class HomeShellViewModel(
    initialState: HomeShellState,
) : MavericksViewModel<HomeShellState>(initialState) {

    init {
        // 首屏先加载分类 Tab（同步），再异步拉 Feed
        setState { copy(tabs = MockRepository.loadHomeTabs()) }
        refresh()
    }

    /** 下拉刷新或首次加载。`execute {}` 由 Mavericks 驱动 Async 生命周期。 */
    fun refresh() {
        setState { copy(refreshing = true) }
        suspend { MockRepository.loadFeed() }
            .execute { result ->
                copy(
                    feed = result,
                    // 成功或失败都关闭下拉动画；仍在 Loading 时保持
                    refreshing = when (result) {
                        is Success, is Fail -> false
                        else -> refreshing
                    },
                )
            }
    }

    /** Tab 切换。当前 mock 数据下不区分 tab，只更新选中索引；真实场景应按 tab 重新请求。 */
    fun selectTab(index: Int) = setState {
        if (index == selectedTabIndex) this else copy(selectedTabIndex = index)
    }

    /**
     * Feed 滚动方向变化时由 BottomBarPage 调用。封装为 VM 方法而不是 Page 本地变量，
     * 才能让 config change 后 BottomBar 自动恢复正确的展开/收起状态。
     */
    fun setBottomBarCollapsed(collapsed: Boolean) = setState {
        if (bottomBarCollapsed == collapsed) this else copy(bottomBarCollapsed = collapsed)
    }
}
