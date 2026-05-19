package com.demo.foundations.slidepane.gesture

import com.demo.foundations.slidepane.PaneApi

/**
 * 纵向手势事件。
 *
 * 框架在 [SlidePaneContainer.dispatchTouchEvent] 早期采样并广播，**只广播不消费**，
 * 业务（如底部 Tab 折叠/展开、悬浮按钮显隐、沉浸式头图）按需订阅。
 */
@PaneApi
sealed class VerticalGestureEvent {

    /** 手指刚按下 */
    @PaneApi
    data class Down(val x: Float, val y: Float) : VerticalGestureEvent()

    /** 手指拖动中 */
    @PaneApi
    data class Drag(
        /** 本次帧间垂直位移（< 0 上滑，> 0 下滑） */
        val dy: Float,
        /** 自 Down 起累计垂直位移 */
        val totalDy: Float,
        /** 实时速度 px/s */
        val velocityY: Float,
        /** 当前主方向（已做去抖） */
        val direction: Direction
    ) : VerticalGestureEvent()

    /** 手指抬起 / 取消 */
    @PaneApi
    data class End(
        val totalDy: Float,
        val flingVelocityY: Float,
        val isFling: Boolean
    ) : VerticalGestureEvent()

    @PaneApi
    enum class Direction {
        /** 手指向上滑（露出页面下方内容，俗称"上滑"） */
        UP,

        /** 手指向下滑 */
        DOWN,

        /** 静止或方向尚未稳定 */
        IDLE
    }
}
