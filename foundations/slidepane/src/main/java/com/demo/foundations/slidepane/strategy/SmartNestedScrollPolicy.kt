package com.demo.foundations.slidepane.strategy

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.NestedScrollingChild3
import androidx.core.view.ViewCompat
import com.demo.foundations.slidepane.PaneApi
import kotlin.math.abs

/**
 * 默认嵌套滑动策略。
 *
 * 决策规则：
 * 1. 必须横向位移 > 纵向位移 * [horizontalDominanceFactor] 才视为横向手势
 * 2. 若触摸点下方存在能消费横向滚动的子 View（例如内嵌的 ViewPager2 / RecyclerView），
 *    优先让子 View 消费；只有当子 View 已到达边界（无法继续横向滚动）时，
 *    框架才接管手势露出侧 Pane
 * 3. [scrollDirection] = +1 表示判定能否向右滚动（露出 START）；-1 表示向左
 *
 * 通过 [View.canScrollHorizontally] 探测子 View 的剩余滚动能力。
 */
@PaneApi
class SmartNestedScrollPolicy(
    /** 横向占优系数：|dx| > |dy| * factor 才认为是横向手势 */
    private val horizontalDominanceFactor: Float = 1.2f
) : NestedScrollPolicy {

    override fun shouldContainerInterceptHorizontal(
        ev: MotionEvent,
        dx: Float,
        dy: Float,
        touchedChild: View?
    ): Boolean {
        // 1. 方向判定
        if (abs(dx) <= abs(dy) * horizontalDominanceFactor) return false

        // 2. 若触摸链路上存在能继续横向滚动的子 View，让它消费
        //    dx > 0 表示手指向右滑，露出 START，子 View 需要"向左滚动"才能继续，即 canScrollHorizontally(-1)
        val direction = if (dx > 0) -1 else 1
        val consumer = findHorizontalScrollConsumer(touchedChild, ev.x, ev.y, direction)
        return consumer == null
    }

    /**
     * 沿 [touchedChild] 向下递归查找：是否存在仍可在 [direction] 方向继续横向滚动的 View
     * direction：+1 = 向右内容滚动；-1 = 向左内容滚动
     */
    private fun findHorizontalScrollConsumer(
        view: View?,
        x: Float,
        y: Float,
        direction: Int
    ): View? {
        if (view == null) return null
        if (canConsumeHorizontalScroll(view, direction)) return view
        if (view !is ViewGroup) return null

        // 找到该点下方的子 View 继续递归
        val localX = x - view.left
        val localY = y - view.top
        for (i in view.childCount - 1 downTo 0) {
            val child = view.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            if (localX >= child.left && localX < child.right &&
                localY >= child.top && localY < child.bottom
            ) {
                val consumer = findHorizontalScrollConsumer(child, localX, localY, direction)
                if (consumer != null) return consumer
            }
        }
        return null
    }

    private fun canConsumeHorizontalScroll(view: View, direction: Int): Boolean {
        // NestedScrollingChild3 标记：明确支持嵌套横向滚动
        if (view is NestedScrollingChild3) {
            // 仍需结合 canScrollHorizontally 判断是否到达边界
            if (view.canScrollHorizontally(direction)) return true
        }
        // 通用判断：View 自身报告可以横向滚动
        return ViewCompat.canScrollHorizontally(view, direction)
    }
}
