package com.demo.foundations.slidepane.strategy

import android.view.View
import androidx.annotation.FloatRange
import com.demo.foundations.slidepane.PaneApi
import com.demo.foundations.slidepane.PaneSlot

/**
 * 过渡动效策略。
 *
 * 滑动过程中持续被回调，业务可通过实现此接口自定义动效（视差 / 推入 / 覆盖 / 缩放 ...）。
 *
 * 注意：实现内禁止创建对象，避免每帧 GC。
 */
@PaneApi
interface PaneTransitionAnimator {

    /**
     * @param center 中心 Pane 的根 View
     * @param side 侧边 Pane 的根 View
     * @param slot 当前正在显露的侧边槽位（START 或 END）
     * @param progress 显露进度 0f..1f
     */
    @PaneApi
    fun onSlide(
        center: View,
        side: View,
        slot: PaneSlot,
        @FloatRange(from = 0.0, to = 1.0) progress: Float
    )

    /**
     * 容器尺寸变化时回调，可用于重新计算过渡参数。
     */
    @PaneApi
    fun onContainerSizeChanged(width: Int, height: Int) { /* no-op */ }
}
