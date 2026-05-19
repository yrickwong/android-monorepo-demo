package com.demo.foundations.slidepane

/**
 * Pane 槽位标识。
 *
 * - [CENTER]：主屏，永远存在
 * - [START]：起始侧（LTR 下为左，RTL 下为右）
 * - [END]：结束侧（LTR 下为右，RTL 下为左）
 *
 * 使用 START/END 而非 LEFT/RIGHT，以原生支持 RTL 语言。
 */
@PaneApi
enum class PaneSlot {
    CENTER,
    START,
    END
}
