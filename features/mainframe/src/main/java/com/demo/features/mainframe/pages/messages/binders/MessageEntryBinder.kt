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
 * 消息列表的"具体一条消息"行 binder（头像 + 标题 + 摘要 + 时间戳 + 未读 badge）。
 *
 * 注册到 [com.demo.features.mainframe.pages.messages.MessagesListPage] 的
 * MultiTypeListPage 中，作用范围是 [MessageRow.Entry] 子类型。
 *
 * 当前 mock 数据没有点击行为，所以本 binder 不和 ShellVM 交互；如果要加
 * "点击进入会话"，在 view.setOnClickListener 里用
 * `ctx.requireConsume(MessagesShellViewModelKey).openChat(item.data.id)` 即可。
 */
internal class MessageEntryBinder : ItemBinder<MessageRow.Entry> {

    override fun createView(parent: ViewGroup, ctx: PageContext): View =
        LayoutInflater.from(parent.context)
            .inflate(R.layout.mainframe_item_message_entry, parent, false)

    override fun bind(
        view: View,
        item: MessageRow.Entry,
        position: Int,
        ctx: PageContext,
    ) {
        val entry = item.data
        val avatar = view.findViewById<View>(R.id.mainframe_msg_avatar)
        val title = view.findViewById<TextView>(R.id.mainframe_msg_title)
        val subtitle = view.findViewById<TextView>(R.id.mainframe_msg_subtitle)
        val timestamp = view.findViewById<TextView>(R.id.mainframe_msg_timestamp)
        val badge = view.findViewById<TextView>(R.id.mainframe_msg_badge)

        avatar.setBackgroundColor(entry.avatarColor)
        title.text = entry.title
        subtitle.text = entry.subtitle
        timestamp.text = entry.timestamp
        if (entry.unreadCount > 0) {
            badge.visibility = View.VISIBLE
            badge.text = if (entry.unreadCount > 99) "99+" else entry.unreadCount.toString()
        } else {
            badge.visibility = View.GONE
        }
    }

    override fun areItemsTheSame(old: MessageRow.Entry, new: MessageRow.Entry): Boolean =
        old.data.id == new.data.id

    override fun areContentsTheSame(old: MessageRow.Entry, new: MessageRow.Entry): Boolean =
        old.data == new.data
}
