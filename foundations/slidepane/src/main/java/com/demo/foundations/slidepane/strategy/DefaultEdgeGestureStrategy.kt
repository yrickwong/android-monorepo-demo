package com.demo.foundations.slidepane.strategy

import android.graphics.Rect
import com.demo.foundations.slidepane.PaneApi
import com.demo.foundations.slidepane.PaneSlot

/**
 * 默认边缘手势策略。
 *
 * - [edgeAllowedDp]：在"仅边缘可触发"模式下，允许触发的边缘宽度
 * - 系统手势排除区：在屏幕左右边缘各预留一段，避免与 Android 10+ 系统返回手势冲突
 */
@PaneApi
class DefaultEdgeGestureStrategy(
    private val density: Float,
    private val edgeAllowedDp: Float = 24f,
    private val systemExclusionDp: Float = 16f
) : EdgeGestureStrategy {

    override fun isEdgeAllowed(slot: PaneSlot, x: Float, viewWidth: Int): Boolean {
        val edgePx = edgeAllowedDp * density
        return when (slot) {
            PaneSlot.START -> x <= edgePx
            PaneSlot.END -> x >= viewWidth - edgePx
            PaneSlot.CENTER -> true
        }
    }

    override fun systemGestureExclusionRects(viewWidth: Int, viewHeight: Int): List<Rect> {
        if (viewWidth <= 0 || viewHeight <= 0) return emptyList()
        val excl = (systemExclusionDp * density).toInt()
        return listOf(
            Rect(0, 0, excl, viewHeight),
            Rect(viewWidth - excl, 0, viewWidth, viewHeight)
        )
    }
}
