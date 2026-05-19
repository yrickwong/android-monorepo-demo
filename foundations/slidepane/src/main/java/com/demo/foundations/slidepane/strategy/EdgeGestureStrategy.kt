package com.demo.foundations.slidepane.strategy

import android.graphics.Rect
import com.demo.foundations.slidepane.PaneApi
import com.demo.foundations.slidepane.PaneSlot

/**
 * 边缘手势策略。
 *
 * 处理 Android 10+ 系统返回手势冲突，并支持"仅边缘可触发"模式。
 */
@PaneApi
interface EdgeGestureStrategy {

    /**
     * 给定触摸点 X 坐标，判断是否处于该槽位的允许触发区。
     */
    @PaneApi
    fun isEdgeAllowed(slot: PaneSlot, x: Float, viewWidth: Int): Boolean

    /**
     * 返回需要排除系统手势的矩形区域，
     * 框架会通过 [android.view.View.setSystemGestureExclusionRects] 应用。
     */
    @PaneApi
    fun systemGestureExclusionRects(viewWidth: Int, viewHeight: Int): List<Rect>
}
