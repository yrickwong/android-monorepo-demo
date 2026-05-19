package com.demo.foundations.slidepane.strategy

import android.view.View
import com.demo.foundations.slidepane.PaneApi
import com.demo.foundations.slidepane.PaneSlot

/**
 * 组合多个 [PaneTransitionAnimator]，按声明顺序依次回调。
 *
 * 用于将"视差 + 遮罩 + ..." 等多种动效叠加，例如：
 * ```
 * container.setTransitionAnimator(
 *     CompositeAnimator(
 *         DefaultParallaxAnimator(),
 *         ScrimAnimator()
 *     )
 * )
 * ```
 *
 * 注意：构造时即固定 animators 数组，不允许运行时增删，避免遍历时并发修改。
 */
@PaneApi
class CompositeAnimator(
    private vararg val animators: PaneTransitionAnimator
) : PaneTransitionAnimator {

    override fun onSlide(center: View, side: View, slot: PaneSlot, progress: Float) {
        // 普通 for 循环避免迭代器分配
        for (i in animators.indices) {
            animators[i].onSlide(center, side, slot, progress)
        }
    }

    override fun onContainerSizeChanged(width: Int, height: Int) {
        for (i in animators.indices) {
            animators[i].onContainerSizeChanged(width, height)
        }
    }
}
