package com.demo.features.mainframe.actions

/**
 * Messages Pane 对外暴露的"业务请求"接口。
 *
 * 与 [ProfilePaneActions] 对称：消息页也只暴露一个"关闭"业务请求。
 */
interface MessagesPaneActions {
    /** 请求关闭消息页，回到 Home（CENTER 槽位）。 */
    fun requestCloseMessages()
}
