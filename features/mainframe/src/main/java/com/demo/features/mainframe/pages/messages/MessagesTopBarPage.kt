package com.demo.features.mainframe.pages.messages

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.demo.features.mainframe.R
import com.demo.features.mainframe.actions.MessagesPaneActionsKey
import com.demo.foundations.assemblekit.ViewPage

/**
 * 消息页顶部栏（关闭 + 居中标题 + 发起会话）。
 *
 * 与 [MessagesListPage] 是兄弟槽位。本 Page 只负责响应关闭点击；
 * "发起会话"按钮目前是装饰，没接行为。
 */
internal class MessagesTopBarPage : ViewPage() {

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View =
        inflater.inflate(R.layout.mainframe_page_messages_top_bar, parent, false)

    override fun onViewCreated(view: View) {
        val actions = requireConsume(MessagesPaneActionsKey)
        view.findViewById<View>(R.id.mainframe_messages_btn_close).setOnClickListener {
            actions.requestCloseMessages()
        }
    }
}
