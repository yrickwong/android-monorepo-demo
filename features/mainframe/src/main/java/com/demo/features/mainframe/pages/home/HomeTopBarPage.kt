package com.demo.features.mainframe.pages.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.demo.features.mainframe.R
import com.demo.features.mainframe.actions.HomePaneActionsKey
import com.demo.foundations.assemblekit.ViewPage

/**
 * Home 顶栏：头像 + 标题 + 消息入口。
 *
 * 业务交互：
 *  - 点头像 → [HomePaneActionsKey] 拿到 actions → `requestOpenProfile()`
 *  - 点消息 → `requestOpenMessages()`
 *
 * 不持有任何状态——所有 click 行为都翻译为对 Actions 的调用。
 */
internal class HomeTopBarPage : ViewPage() {

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View =
        inflater.inflate(R.layout.mainframe_page_home_top_bar, parent, false)

    override fun onViewCreated(view: View) {
        val actions = requireConsume(HomePaneActionsKey)
        view.findViewById<View>(R.id.mainframe_home_btn_avatar).setOnClickListener {
            actions.requestOpenProfile()
        }
        view.findViewById<View>(R.id.mainframe_home_btn_messages).setOnClickListener {
            actions.requestOpenMessages()
        }
    }
}
