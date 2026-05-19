@file:OptIn(com.airbnb.mvrx.InternalMavericksApi::class)

package com.demo.features.mainframe.pages.messages

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
import com.demo.features.mainframe.actions.MessagesPaneActions
import com.demo.features.mainframe.actions.MessagesPaneActionsKey
import com.demo.features.mainframe.state.MessagesShellState
import com.demo.features.mainframe.state.MessagesShellViewModel
import com.demo.features.mainframe.state.MessagesShellViewModelKey
import com.demo.foundations.assemblekit.Assembly
import com.demo.foundations.assemblekit.PageHostFragment
import com.demo.foundations.assemblekit.assemble
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Messages Pane（END 槽位）的 PageHost。
 *
 * 同 Home / Profile 的结构：
 *  1) 持有 [MessagesShellViewModel]——Fragment 作用域，跨 config change 保活。
 *  2) 把宿主 Activity 提供的 [MessagesPaneActions] 注入到 hostLocal。
 *  3) 用 `assemble {}` 把 2 个 Page 钉到 2 个槽位。
 *  4) 处理状态栏 inset。
 */
class MessagesPaneHostFragment : PageHostFragment(R.layout.mainframe_fragment_messages_pane_host) {

    private lateinit var viewModel: MessagesShellViewModel
    private lateinit var messagesAssembly: Assembly

    override fun onAttach(context: Context) {
        super.onAttach(context)
        val actions = context as? MessagesPaneActions
            ?: error("Host Activity must implement MessagesPaneActions")
        hostLocal[MessagesPaneActionsKey] = actions
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.mainframe_fragment_messages_pane_host, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyWindowInsets(view)
        viewModel = createShellViewModel()
        installAssembly()
    }

    private fun applyWindowInsets(root: View) {
        val statusInset = root.findViewById<View>(R.id.mainframe_messages_status_inset)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            statusInset.updateLayoutParams { height = sysBars.top }
            insets
        }
    }

    private fun createShellViewModel(): MessagesShellViewModel =
        MavericksViewModelProvider.get(
            viewModelClass = MessagesShellViewModel::class.java,
            stateClass = MessagesShellState::class.java,
            viewModelContext = FragmentViewModelContext(
                activity = requireActivity(),
                args = null,
                fragment = this,
            ),
            key = "mainframe_messages_shell",
        )

    private fun installAssembly() {
        val rowsFlow = viewModel.stateFlow
            .map { it.messageRows }
            .distinctUntilChanged()

        messagesAssembly = assemble {
            provides(MessagesShellViewModelKey, viewModel)

            +MessagesTopBarPage() at R.id.mainframe_messages_slot_top_bar
            +MessagesListPage(rowsFlow) at R.id.mainframe_messages_slot_list
        }
    }
}
