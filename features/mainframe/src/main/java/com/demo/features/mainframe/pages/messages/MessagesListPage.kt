package com.demo.features.mainframe.pages.messages

import com.demo.features.mainframe.pages.messages.binders.MessageEntryBinder
import com.demo.features.mainframe.pages.messages.binders.MessageSectionBinder
import com.demo.features.mainframe.state.MessageRow
import com.demo.foundations.assemblekit.list.MultiTypeListPage
import kotlinx.coroutines.flow.Flow

/**
 * 消息列表 Page（END 槽位的核心区域）。
 *
 * 与 Profile / Home 不同，消息列表是 [com.demo.foundations.assemblekit.list.MultiTypeListPage]
 * 的"标准用法场景"：
 *  - LinearLayoutManager（不需要 isFullSpan）
 *  - 不需要测列宽
 *  - 不需要 SwipeRefreshLayout 包裹
 *
 * 所以本 Page 只是一层薄薄的 MultiTypeListPage 子类——`itemsFlow` 由
 * Host 从 [com.demo.features.mainframe.state.MessagesShellState.messageRows]
 * 派生出来传进来，binders 注册 Section / Entry 两类。
 */
internal class MessagesListPage(
    rowsFlow: Flow<List<MessageRow>>,
) : MultiTypeListPage<MessageRow>(
    itemsFlow = rowsFlow,
    binders = {
        // 注册顺序：先具体后通用——本例两类是兄弟、互不相交，顺序无所谓，
        // 但养成"具体类型先注册"的习惯能避免之后加共同祖先时踩坑。
        bind<MessageRow.Section>(MessageSectionBinder())
        bind<MessageRow.Entry>(MessageEntryBinder())
    },
)
