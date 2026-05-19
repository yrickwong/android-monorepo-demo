package com.demo.foundations.slidepane

/**
 * 实时滑动进度。
 *
 * @property activeSlot 当前正在被显露的侧 Pane 槽位。如果中心页处于完全居中状态，
 *                     该字段值为 [PaneSlot.CENTER]。
 * @property fraction 显露进度，范围 [0f, 1f]。0f 表示侧 Pane 完全隐藏，1f 表示完全打开。
 * @property isUserDragging 当前是否处于用户手指拖动中。
 */
@PaneApi
data class SlideProgress(
    val activeSlot: PaneSlot,
    val fraction: Float,
    val isUserDragging: Boolean
) {
    companion object {
        @PaneApi
        val Idle: SlideProgress = SlideProgress(PaneSlot.CENTER, 0f, false)
    }
}
