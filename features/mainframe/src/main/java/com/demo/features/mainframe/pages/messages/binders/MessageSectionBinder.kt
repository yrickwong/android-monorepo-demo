package com.demo.features.mainframe.pages.messages.binders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.demo.features.mainframe.R
import com.demo.features.mainframe.state.MessageRow
import com.demo.foundations.assemblekit.PageContext
import com.demo.foundations.assemblekit.list.ItemBinder

/**
 * 消息列表的"分组标题"行 binder（如"通知"、"私信"）。
 *
 * 注册到 [com.demo.features.mainframe.pages.messages.MessagesListPage] 的
 * MultiTypeListPage 中，作用范围是 [MessageRow.Section] 子类型。
 */
internal class MessageSectionBinder : ItemBinder<MessageRow.Section> {

    override fun createView(parent: ViewGroup, ctx: PageContext): View =
        LayoutInflater.from(parent.context)
            .inflate(R.layout.mainframe_item_message_section, parent, false)

    override fun bind(
        view: View,
        item: MessageRow.Section,
        position: Int,
        ctx: PageContext,
    ) {
        (view as TextView).text = item.title
    }

    override fun areItemsTheSame(old: MessageRow.Section, new: MessageRow.Section): Boolean =
        old.title == new.title
}
