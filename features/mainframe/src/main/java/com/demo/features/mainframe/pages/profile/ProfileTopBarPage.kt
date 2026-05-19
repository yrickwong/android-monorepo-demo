package com.demo.features.mainframe.pages.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.demo.features.mainframe.R
import com.demo.features.mainframe.actions.ProfilePaneActionsKey
import com.demo.foundations.assemblekit.ViewPage

/**
 * 个人页顶部浮动操作栏（关闭 + 右侧的搜索/分享/菜单图标）。
 *
 * 与 [ProfileContentPage] 在 ConstraintLayout 中是兄弟槽位——它浮在内容之上，
 * z-order 由 XML 顺序决定。本 Page 只负责响应关闭点击，把动作翻译成对宿主
 * Activity 的请求。
 */
internal class ProfileTopBarPage : ViewPage() {

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View =
        inflater.inflate(R.layout.mainframe_page_profile_top_bar, parent, false)

    override fun onViewCreated(view: View) {
        val actions = requireConsume(ProfilePaneActionsKey)
        view.findViewById<View>(R.id.mainframe_profile_btn_close).setOnClickListener {
            actions.requestCloseProfile()
        }
    }
}
