package com.demo.foundations.slidepane.gesture

import com.demo.foundations.slidepane.PaneApi

/**
 * 纵向手势观察者。
 *
 * 注册后会收到完整的事件序列：Down → Drag* → End。
 *
 * **注意**：业务实现内禁止做耗时操作，会阻塞主线程触摸分发。
 */
@PaneApi
fun interface VerticalGestureObserver {
    @PaneApi
    fun onVerticalGesture(event: VerticalGestureEvent)
}
