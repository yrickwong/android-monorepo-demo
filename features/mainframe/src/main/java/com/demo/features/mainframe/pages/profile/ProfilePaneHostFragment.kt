@file:OptIn(com.airbnb.mvrx.InternalMavericksApi::class)

package com.demo.features.mainframe.pages.profile

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.airbnb.mvrx.FragmentViewModelContext
import com.airbnb.mvrx.MavericksViewModelProvider
import com.demo.features.mainframe.R
import com.demo.features.mainframe.actions.ProfilePaneActions
import com.demo.features.mainframe.actions.ProfilePaneActionsKey
import com.demo.features.mainframe.state.ProfileShellState
import com.demo.features.mainframe.state.ProfileShellViewModel
import com.demo.features.mainframe.state.ProfileShellViewModelKey
import com.demo.foundations.assemblekit.Assembly
import com.demo.foundations.assemblekit.PageHostFragment
import com.demo.foundations.assemblekit.assemble

/**
 * Profile Pane（START 槽位）的 PageHost。
 *
 * 与 [com.demo.features.mainframe.pages.home.HomePaneHostFragment] 同构：
 *  1) 持有 [ProfileShellViewModel]——Fragment 作用域，跨 config change 保活。
 *  2) 把宿主 Activity 提供的 [ProfilePaneActions] 注入到 hostLocal，让所有 Page
 *     可以通过 `requireConsume(ProfilePaneActionsKey)` 拿到。
 *  3) 用 `assemble {}` 把 2 个 Page 钉到 2 个 FrameLayout 槽位。
 *  4) 处理状态栏 inset，让浮动的 TopBar 避开系统区域。
 *
 * 完全不感知 [com.demo.foundations.slidepane.SlidePaneContainer]——只通过
 * [ProfilePaneActions] 接口和宿主对话。
 */
class ProfilePaneHostFragment : PageHostFragment(R.layout.mainframe_fragment_profile_pane_host) {

    private lateinit var viewModel: ProfileShellViewModel
    private lateinit var profileAssembly: Assembly

    override fun onAttach(context: Context) {
        super.onAttach(context)
        val actions = context as? ProfilePaneActions
            ?: error("Host Activity must implement ProfilePaneActions")
        hostLocal[ProfilePaneActionsKey] = actions
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.mainframe_fragment_profile_pane_host, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyWindowInsets(view)
        viewModel = createShellViewModel()
        installAssembly()
    }

    private fun applyWindowInsets(root: View) {
        val statusInset = root.findViewById<View>(R.id.mainframe_profile_status_inset)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            statusInset.updateLayoutParams { height = sysBars.top }
            insets
        }
    }

    private fun createShellViewModel(): ProfileShellViewModel =
        MavericksViewModelProvider.get(
            viewModelClass = ProfileShellViewModel::class.java,
            stateClass = ProfileShellState::class.java,
            viewModelContext = FragmentViewModelContext(
                activity = requireActivity(),
                args = null,
                fragment = this,
            ),
            key = "mainframe_profile_shell",
        )

    private fun installAssembly() {
        profileAssembly = assemble {
            // 把 ShellVM 暴露给所有子 Page；Pages 通过 requireConsume 拿
            provides(ProfileShellViewModelKey, viewModel)

            +ProfileContentPage() at R.id.mainframe_profile_slot_content
            +ProfileTopBarPage() at R.id.mainframe_profile_slot_top_bar
        }
    }
}
