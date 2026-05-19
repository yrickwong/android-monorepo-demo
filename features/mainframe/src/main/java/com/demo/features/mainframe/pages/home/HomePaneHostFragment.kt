@file:OptIn(com.airbnb.mvrx.InternalMavericksApi::class)

package com.demo.features.mainframe.pages.home

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.airbnb.mvrx.FragmentViewModelContext
import com.airbnb.mvrx.MavericksViewModelProvider
import com.demo.features.mainframe.R
import com.demo.features.mainframe.actions.HomePaneActions
import com.demo.features.mainframe.actions.HomePaneActionsKey
import com.demo.features.mainframe.state.HomeShellState
import com.demo.features.mainframe.state.HomeShellViewModel
import com.demo.features.mainframe.state.HomeShellViewModelKey
import com.demo.foundations.assemblekit.Assembly
import com.demo.foundations.assemblekit.PageHostFragment
import com.demo.foundations.assemblekit.assemble
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Home Pane（CENTER 槽位）的 PageHost。
 *
 * 职责：
 *  1) 持有 [HomeShellViewModel]——Fragment 作用域，跨 config change 保活。
 *  2) 把宿主 Activity 提供的 [HomePaneActions] 注入到 hostLocal，让所有 Page
 *     可以通过 `requireConsume(HomePaneActionsKey)` 拿到。
 *  3) 用 `assemble {}` 把 4 个 Page 钉到 4 个 FrameLayout 槽位。
 *  4) 处理状态栏 / 导航栏 inset，让顶栏和底栏避开系统区域。
 *
 * 完全不感知 [com.demo.foundations.slidepane.SlidePaneContainer]——它只通过
 * [HomePaneActions] 接口和宿主对话。
 */
class HomePaneHostFragment : PageHostFragment(R.layout.mainframe_fragment_home_pane_host) {

    private lateinit var viewModel: HomeShellViewModel
    private lateinit var homeAssembly: Assembly

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // 宿主 Activity 必须实现 HomePaneActions——把它放进 hostLocal 之后，所有 Page 都
        // 能通过 requireConsume(HomePaneActionsKey) 拿到，无需穿构造参数。
        val actions = context as? HomePaneActions
            ?: error("Host Activity must implement HomePaneActions")
        hostLocal[HomePaneActionsKey] = actions
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.mainframe_fragment_home_pane_host, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyWindowInsets(view)
        viewModel = createShellViewModel()
        installAssembly()
    }

    private fun applyWindowInsets(root: View) {
        val topInset = root.findViewById<View>(R.id.mainframe_home_top_bar_inset)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // 顶部留状态栏高度
            topInset.updateLayoutParams { height = sysBars.top }
            // 底部 slot 加 padding 让悬浮工具栏避开导航栏
            root.findViewById<View>(R.id.mainframe_home_slot_bottom_bar)
                .updatePadding(bottom = sysBars.bottom)
            insets
        }
    }

    private fun createShellViewModel(): HomeShellViewModel =
        MavericksViewModelProvider.get(
            viewModelClass = HomeShellViewModel::class.java,
            stateClass = HomeShellState::class.java,
            viewModelContext = FragmentViewModelContext(
                activity = requireActivity(),
                args = null,
                fragment = this,
            ),
            key = "mainframe_home_shell",
        )

    private fun installAssembly() {
        // Tab 列表来源：state 的派生 tabRows，去重后丢给 ListPage
        val tabFlow = viewModel.stateFlow
            .map { it.tabRows }
            .distinctUntilChanged()

        homeAssembly = assemble {
            // 把 ShellVM 暴露给所有子 Page；Pages 通过 requireConsume 拿
            provides(HomeShellViewModelKey, viewModel)

            +HomeTopBarPage() at R.id.mainframe_home_slot_top_bar
            +HomeTabsPage(tabFlow) at R.id.mainframe_home_slot_tabs
            +HomeFeedPage() at R.id.mainframe_home_slot_feed
            +HomeBottomBarPage() at R.id.mainframe_home_slot_bottom_bar
        }
    }
}
