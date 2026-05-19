package com.demo.features.mainframe.state

import com.airbnb.mvrx.Async
import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.Uninitialized
import com.demo.features.mainframe.data.MockRepository

/**
 * Messages Pane 的 Shell State（END 槽位）。
 *
 * 设计：
 *  - 原始数据 [messages]：Async 包裹的扁平 entry 列表。
 *  - 派生 [messageRows]：把扁平 entry 按 type 分成"通知"和"私信"两段，
 *    每段前面插一个 Section header。Pages 直接订阅这个派生列表，
 *    给 [com.demo.foundations.assemblekit.list.MultiTypeListPage] 用。
 *
 * 在 state 上做派生而不是在 Page 里做 Flow.map，是为了让"消息列表的展示结构"
 * 这个领域规则集中在一处——以后要加"广告 banner 行"、"折叠分组"之类的
 * 改动，只动 state，Page 完全不感知。
 */
data class MessagesShellState(
    val messages: Async<List<MockRepository.MessageEntry>> = Uninitialized,
) : MavericksState {

    /** 派生：把 entries 拼成 Section + Entry 的 row 列表。Loading/Fail 时为空。 */
    val messageRows: List<MessageRow> by lazy {
        val entries = messages.invoke() ?: return@lazy emptyList()
        buildRows(entries)
    }

    private fun buildRows(entries: List<MockRepository.MessageEntry>): List<MessageRow> {
        val notifications = entries.filter { it.type != MockRepository.MessageEntry.Type.CHAT }
        val chats = entries.filter { it.type == MockRepository.MessageEntry.Type.CHAT }
        val out = mutableListOf<MessageRow>()
        if (notifications.isNotEmpty()) {
            out += MessageRow.Section("通知")
            notifications.forEach { out += MessageRow.Entry(it) }
        }
        if (chats.isNotEmpty()) {
            out += MessageRow.Section("私信")
            chats.forEach { out += MessageRow.Entry(it) }
        }
        return out
    }
}

/**
 * 消息列表的 row 模型：要么是分组标题，要么是一条具体 entry。
 * 注册到 [com.demo.foundations.assemblekit.list.MultiTypeListPage] 时一一对应到 binder。
 */
sealed class MessageRow {
    data class Section(val title: String) : MessageRow()
    data class Entry(val data: MockRepository.MessageEntry) : MessageRow()
}
