package com.demo.features.mainframe.state

import com.demo.foundations.assemblekit.local.pageContextKey

/**
 * 三个 Shell ViewModel 在 PageContext 中的 key。
 *
 * 用法（在 PaneHostFragment 中创建 VM 后）：
 * ```kotlin
 * val vm = MavericksViewModelProvider.get(...)
 * assemble(container = ...) {
 *     provides(HomeShellViewModelKey, vm)
 *     +HomeTopBarPage() at R.id.mainframe_home_slot_top_bar
 *     ...
 * }
 * ```
 *
 * Pages 通过 `requireConsume(HomeShellViewModelKey)` 拿到 VM 实例。
 */
val HomeShellViewModelKey = pageContextKey<HomeShellViewModel>("mainframe.homeShellVm")

val ProfileShellViewModelKey = pageContextKey<ProfileShellViewModel>("mainframe.profileShellVm")

val MessagesShellViewModelKey = pageContextKey<MessagesShellViewModel>("mainframe.messagesShellVm")
