package com.demo.foundations.slidepane.strategy

import android.view.View
import com.demo.foundations.slidepane.PaneApi
import com.demo.foundations.slidepane.PaneSlot

/**
 * 默认视差动效（小红书风格）：
 *
 * - 中心页跟手平移
 * - 侧 Pane 以 1/4 容器宽度做反向预偏移，逐步归位（视差感）
 * - 侧 Pane 透明度 0.5 → 1
 * - 可选轻微缩放（默认关闭，避免低端机掉帧）
 */
@PaneApi
class DefaultParallaxAnimator(
    private val parallaxFactor: Float = 0.1f,
    private val minAlpha: Float = 0.6f,
    private val enableScale: Boolean = false,
    private val minScale: Float = 0.94f
) : PaneTransitionAnimator {

    private var containerWidth: Int = 0

    override fun onContainerSizeChanged(width: Int, height: Int) {
        containerWidth = width
    }

    override fun onSlide(
        center: View,
        side: View,
        slot: PaneSlot,
        progress: Float
    ) {
        val w = if (containerWidth > 0) containerWidth else center.width
        if (w == 0) return

        // 视差预偏移方向：START 时侧页位于左侧，初始向左偏移；END 反之
        val direction = if (slot == PaneSlot.START) -1 else 1
        side.translationX = direction * w * parallaxFactor * (1f - progress)
        side.alpha = minAlpha + (1f - minAlpha) * progress

        if (enableScale) {
            val scale = minScale + (1f - minScale) * progress
            side.scaleX = scale
            side.scaleY = scale
        } else if (side.scaleX != 1f) {
            side.scaleX = 1f
            side.scaleY = 1f
        }
    }
}
