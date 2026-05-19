package com.demo.foundations.slidepane.lifecycle

import com.demo.foundations.slidepane.PaneApi
import com.demo.foundations.slidepane.PaneSlot
import com.demo.foundations.slidepane.SlideProgress

/**
 * Pane 生命周期观察者，框架对外暴露的稳定事件序列：
 *
 * ```
 *  WillAppear → onSlideProgress(0..1) → DidAppear
 *  WillDisappear → onSlideProgress(1..0) → DidDisappear
 * ```
 *
 * 业务可基于此做：埋点、首屏指标、动画联动、预加载等。
 */
@PaneApi
interface PaneLifecycleObserver {
    @PaneApi fun onPaneWillAppear(slot: PaneSlot) {}
    @PaneApi fun onPaneDidAppear(slot: PaneSlot) {}
    @PaneApi fun onPaneWillDisappear(slot: PaneSlot) {}
    @PaneApi fun onPaneDidDisappear(slot: PaneSlot) {}
    @PaneApi fun onSlideProgress(progress: SlideProgress) {}
}
