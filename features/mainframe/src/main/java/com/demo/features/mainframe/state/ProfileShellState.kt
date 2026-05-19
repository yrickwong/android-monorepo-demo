package com.demo.features.mainframe.state

import com.airbnb.mvrx.Async
import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.Uninitialized
import com.demo.features.mainframe.data.MockRepository

/**
 * Profile Pane 的 Shell State（START 槽位）。
 *
 * 个人页结构简单：一个 Header + 一组 Note 卡片，所以只用一个 Async 字段承载整份
 * [MockRepository.UserProfile]。Pages 拿到后自行拆分成两类 ItemBinder 渲染。
 */
data class ProfileShellState(
    val profile: Async<MockRepository.UserProfile> = Uninitialized,
) : MavericksState
