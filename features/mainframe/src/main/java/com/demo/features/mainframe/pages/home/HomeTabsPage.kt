package com.demo.features.mainframe.pages.home

import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.demo.features.mainframe.pages.home.binders.HomeTabBinder
import com.demo.features.mainframe.state.HomeTabRow
import com.demo.foundations.assemblekit.list.ListPage
import kotlinx.coroutines.flow.Flow

/**
 * Home 顶部分类 Tab 横向滚动列表。
 *
 * 复用框架的 [ListPage]——把 LayoutManager 切成 HORIZONTAL 即可。Tab 选中态由
 * [HomeTabRow.selected] 携带，binder 不持有任何外部状态——选中切换走"Page → VM →
 * state → tabRows 重新生成 → DiffUtil 重绘两行"的标准 MVI 链路。
 *
 * 父 Page 的 PageContext 已经在 [com.demo.features.mainframe.pages.home.HomePaneHostFragment]
 * 的 `assemble {}` 里 `provides(HomeShellViewModelKey, vm)`，所以
 * [HomeTabBinder] 在每行 view 上 `requireConsume` 都能拿到。
 */
internal class HomeTabsPage(
    tabRows: Flow<List<HomeTabRow>>,
) : ListPage<HomeTabRow>(
    itemsFlow = tabRows,
    itemBinder = HomeTabBinder,
    layoutManagerFactory = { parent: ViewGroup ->
        LinearLayoutManager(parent.context, LinearLayoutManager.HORIZONTAL, false)
    },
)
