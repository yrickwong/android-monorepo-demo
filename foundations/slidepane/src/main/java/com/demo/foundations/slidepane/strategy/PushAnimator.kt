package com.demo.foundations.slidepane.strategy

import android.view.View
import com.demo.foundations.slidepane.PaneApi
import com.demo.foundations.slidepane.PaneSlot

/**
 * 推入式动效：侧 Pane 等比例推动中心 Pane（类似 ViewPager 翻页）。
 *
 * 与 [DefaultParallaxAnimator] 的区别：侧页与中心页等速移动，无视差。
 */
@PaneApi
class PushAnimator : PaneTransitionAnimator {

    private var containerWidth: Int = 0

    override fun onContainerSizeChanged(width: Int, height: Int) {
        containerWidth = width
    }

    override fun onSlide(center: View, side: View, slot: PaneSlot, progress: Float) {
        val w = if (containerWidth > 0) containerWidth else center.width
        if (w == 0) return
        val direction = if (slot == PaneSlot.START) -1 else 1
        side.translationX = direction * w * (1f - progress)
        side.alpha = 1f
    }
}
