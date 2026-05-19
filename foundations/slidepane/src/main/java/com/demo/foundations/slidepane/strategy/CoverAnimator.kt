package com.demo.foundations.slidepane.strategy

import android.view.View
import com.demo.foundations.slidepane.PaneApi
import com.demo.foundations.slidepane.PaneSlot

/**
 * 覆盖式动效（类微信侧滑）：
 *
 * - 中心页跟手平移
 * - 侧 Pane 始终位于其槽位下方，无视差，无透明度变化
 *
 * 性能开销最低，适合低端机降级方案。
 */
@PaneApi
class CoverAnimator : PaneTransitionAnimator {
    override fun onSlide(center: View, side: View, slot: PaneSlot, progress: Float) {
        side.translationX = 0f
        side.alpha = 1f
    }
}
