package com.demo.foundations.slidepane.gesture.internal

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import com.demo.foundations.slidepane.gesture.ScrollDirectionListener
import com.demo.foundations.slidepane.gesture.VerticalGestureEvent
import com.demo.foundations.slidepane.gesture.VerticalGestureObserver
import kotlin.math.abs

/**
 * 纵向手势广播器（框架内部使用）。
 *
 * 设计原则：
 * - **只观察不消费**：在 [SlidePaneContainer.dispatchTouchEvent] 早期采样
 * - **零分配热路径**：滑动期间避免任何对象创建
 * - **去抖**：方向判定基于阈值 + 滑动平均，避免业务侧自行去抖
 * - **生命周期安全**：业务侧负责注册/反注册
 */
internal class VerticalGestureBroadcaster(context: Context) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val flingVelocityThreshold = ViewConfiguration.get(context)
        .scaledMinimumFlingVelocity.toFloat()

    private var velocityTracker: VelocityTracker? = null

    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var totalDy = 0f
    private var lastDirection = VerticalGestureEvent.Direction.IDLE
    private var enabled = true

    private val observers = mutableListOf<VerticalGestureObserver>()
    private val directionListeners = mutableListOf<DirectionListenerEntry>()

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun addObserver(observer: VerticalGestureObserver) {
        observers.add(observer)
    }

    fun removeObserver(observer: VerticalGestureObserver) {
        observers.remove(observer)
    }

    fun addDirectionListener(thresholdPx: Int, listener: ScrollDirectionListener) {
        directionListeners.add(DirectionListenerEntry(thresholdPx.toFloat(), listener))
    }

    fun removeDirectionListener(listener: ScrollDirectionListener) {
        directionListeners.removeAll { it.listener === listener }
    }

    /**
     * 在 [SlidePaneContainer.dispatchTouchEvent] 中调用。
     * 始终返回（不影响事件正常传递）。
     */
    fun observe(ev: MotionEvent) {
        if (!enabled) return
        if (observers.isEmpty() && directionListeners.isEmpty()) return

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleDown(ev)
            MotionEvent.ACTION_MOVE -> handleMove(ev)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handleUp(ev)
        }
    }

    private fun handleDown(ev: MotionEvent) {
        downX = ev.x
        downY = ev.y
        lastY = ev.y
        totalDy = 0f
        lastDirection = VerticalGestureEvent.Direction.IDLE

        velocityTracker?.recycle()
        velocityTracker = VelocityTracker.obtain().apply { addMovement(ev) }

        dispatch(VerticalGestureEvent.Down(ev.x, ev.y))
    }

    private fun handleMove(ev: MotionEvent) {
        val tracker = velocityTracker ?: return
        tracker.addMovement(ev)

        val dy = ev.y - lastY
        lastY = ev.y
        totalDy = ev.y - downY

        // 速度采样（每次都计算一次成本可控；如需优化可限频）
        tracker.computeCurrentVelocity(1000)
        val velocityY = tracker.yVelocity

        val newDirection = when {
            abs(totalDy) < touchSlop -> VerticalGestureEvent.Direction.IDLE
            totalDy < 0 -> VerticalGestureEvent.Direction.UP
            else -> VerticalGestureEvent.Direction.DOWN
        }

        if (observers.isNotEmpty()) {
            dispatch(VerticalGestureEvent.Drag(dy, totalDy, velocityY, newDirection))
        }

        // 方向变化通知（带阈值去抖）
        if (newDirection != lastDirection) {
            directionListeners.forEach { entry ->
                if (abs(totalDy) >= entry.thresholdPx ||
                    newDirection == VerticalGestureEvent.Direction.IDLE
                ) {
                    entry.listener.onScrollDirectionChanged(newDirection)
                }
            }
            lastDirection = newDirection
        }
    }

    private fun handleUp(ev: MotionEvent) {
        val tracker = velocityTracker
        var velocity = 0f
        if (tracker != null) {
            tracker.addMovement(ev)
            tracker.computeCurrentVelocity(1000)
            velocity = tracker.yVelocity
            tracker.recycle()
            velocityTracker = null
        }
        val isFling = abs(velocity) >= flingVelocityThreshold
        dispatch(VerticalGestureEvent.End(totalDy, velocity, isFling))

        // 抬起时统一回到 IDLE
        if (lastDirection != VerticalGestureEvent.Direction.IDLE) {
            directionListeners.forEach {
                it.listener.onScrollDirectionChanged(VerticalGestureEvent.Direction.IDLE)
            }
            lastDirection = VerticalGestureEvent.Direction.IDLE
        }
    }

    private fun dispatch(event: VerticalGestureEvent) {
        // toList 防止迭代中并发修改
        val snapshot = observers.toList()
        for (i in snapshot.indices) {
            snapshot[i].onVerticalGesture(event)
        }
    }

    private class DirectionListenerEntry(
        val thresholdPx: Float,
        val listener: ScrollDirectionListener
    )
}
