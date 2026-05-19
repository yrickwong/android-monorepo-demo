package com.demo.foundations.slidepane.strategy

import android.view.MotionEvent
import android.view.View
import com.demo.foundations.slidepane.PaneApi

/**
 * 嵌套滑动决策策略。
 *
 * 容器在 [onInterceptTouchEvent] 早期询问策略：是否应该让父容器拿走横向手势？
 *
 * 此接口预留 [touchedChild] 参数，便于业务做精细化判断
 * （如：触摸点在子 ViewPager2 上时，由策略决定是先让子消费还是父消费）。
 */
@PaneApi
interface NestedScrollPolicy {

    /**
     * @param ev 当前 MotionEvent
     * @param dx 自 DOWN 起累计水平位移
     * @param dy 自 DOWN 起累计垂直位移
     * @param touchedChild 触摸点下方最浅层的子 View（可能为 null）
     * @return true 表示父容器拦截手势；false 表示交给子 View 处理
     */
    @PaneApi
    fun shouldContainerInterceptHorizontal(
        ev: MotionEvent,
        dx: Float,
        dy: Float,
        touchedChild: View?
    ): Boolean
}
