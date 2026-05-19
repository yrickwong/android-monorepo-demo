package com.demo.features.mainframe.actions

import com.demo.foundations.assemblekit.local.pageContextKey

/**
 * 三个 Pane Actions 在 PageContext 中的 key。
 *
 * 用法（在 PaneHostFragment.onAttach 中）：
 * ```kotlin
 * override fun onAttach(context: Context) {
 *     super.onAttach(context)
 *     hostLocal[HomePaneActionsKey] = context as HomePaneActions
 * }
 * ```
 *
 * Pages 通过 `requireConsume(HomePaneActionsKey)` 拿到实现去调业务方法，
 * 全程不依赖 MainActivity / SlidePaneContainer 的具体类型——这就是 AssembleKit
 * "view-tree 注入"模式的标准用法。
 */
val HomePaneActionsKey = pageContextKey<HomePaneActions>("mainframe.homePaneActions")

val ProfilePaneActionsKey = pageContextKey<ProfilePaneActions>("mainframe.profilePaneActions")

val MessagesPaneActionsKey = pageContextKey<MessagesPaneActions>("mainframe.messagesPaneActions")
