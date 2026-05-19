package com.demo.foundations.slidepane.gesture

import com.demo.foundations.slidepane.PaneApi

/**
 * 简化版纵向手势监听器：仅在方向稳定切换时回调。
 *
 * 99% 场景（折叠 Tab、悬浮按钮显隐）使用此接口即可，无需自行做去抖。
 */
@PaneApi
fun interface ScrollDirectionListener {
    @PaneApi
    fun onScrollDirectionChanged(direction: VerticalGestureEvent.Direction)
}
