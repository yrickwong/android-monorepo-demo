package com.demo.features.mainframe.state

import com.airbnb.mvrx.MavericksViewModel
import com.demo.features.mainframe.data.MockRepository

/**
 * Messages Pane 的 Shell ViewModel（END 槽位）。
 */
class MessagesShellViewModel(
    initialState: MessagesShellState,
) : MavericksViewModel<MessagesShellState>(initialState) {

    init { refresh() }

    fun refresh() {
        suspend { MockRepository.loadMessages() }.execute { copy(messages = it) }
    }
}
