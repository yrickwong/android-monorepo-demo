package com.demo.features.mainframe.actions

/**
 * Profile Pane 对外暴露的"业务请求"接口。
 *
 * 个人页只关心一件事：用户点了"关闭"按钮，需要回到 Home。具体怎么关
 * （动画 / 阻尼 / 是否清栈）由 MainActivity 的实现决定。
 */
interface ProfilePaneActions {
    /** 请求关闭个人页，回到 Home（CENTER 槽位）。 */
    fun requestCloseProfile()
}
