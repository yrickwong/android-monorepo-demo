package com.demo.features.mainframe.state

import com.airbnb.mvrx.MavericksViewModel
import com.demo.features.mainframe.data.MockRepository

/**
 * Profile Pane 的 Shell ViewModel（START 槽位）。
 *
 * 生命周期跟随 [com.demo.features.mainframe.pages.profile.ProfilePaneHostFragment]。
 * 行为很简单：首屏加载一次 mock 数据，之后没有外部刷新触发。
 */
class ProfileShellViewModel(
    initialState: ProfileShellState,
) : MavericksViewModel<ProfileShellState>(initialState) {

    init { refresh() }

    fun refresh() {
        suspend { MockRepository.loadCurrentUser() }.execute { copy(profile = it) }
    }
}
