package com.demo.foundations.slidepane.strategy

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import com.demo.foundations.slidepane.PaneApi
import com.demo.foundations.slidepane.PaneSlot

/**
 * 侧 Pane 入场遮罩动效。
 *
 * 滑动露出侧 Pane 的过程中，给**侧 Pane**叠加一层"逐渐变浅"的半透明遮罩：
 * - progress=0（刚开始露头）：遮罩最不透明，侧 Pane 像被一层薄雾盖住
 * - progress=1（完全展开）：遮罩完全透明，侧 Pane 清晰可见
 *
 * 这种"由蒙层 → 清晰"的过渡能弱化侧 Pane 一开始突然出现的视觉割裂感，
 * 与 [DefaultParallaxAnimator] 的位移视差叠加后，整体观感更接近原生小红书。
 *
 * 实现细节：
 * - 通过 [View.setForeground] 缓存一个 [ColorDrawable]，每帧仅修改 alpha，无 GC
 * - 当 progress 回到 0、且槽位关闭时，会自动把 foreground 置回完全透明
 * - 与其它 Animator 可通过 [CompositeAnimator] 叠用
 *
 * @param scrimColor 遮罩基础颜色（默认白色），alpha 通道会按 [maxAlpha] 与 progress 计算
 * @param maxAlpha 遮罩最大不透明度（progress=0f 时），范围 0f..1f
 */
@PaneApi
class ScrimAnimator @JvmOverloads constructor(
    @ColorInt private val scrimColor: Int = Color.WHITE,
    @FloatRange(from = 0.0, to = 1.0) private val maxAlpha: Float = 0.9f
) : PaneTransitionAnimator {

    private val baseR = (scrimColor shr 16) and 0xFF
    private val baseG = (scrimColor shr 8) and 0xFF
    private val baseB = scrimColor and 0xFF

    /** 复用同一个 ColorDrawable，避免每帧 new */
    private val scrimDrawable = ColorDrawable(scrimColor and 0x00FFFFFF)

    /** 已挂载 foreground 的侧 View，用于切换槽位时把上一面的遮罩清掉 */
    private var attachedSide: View? = null

    override fun onSlide(center: View, side: View, slot: PaneSlot, progress: Float) {
        // 切换到另一侧时，先把上一面的 foreground 清干净，避免残留
        if (attachedSide !== side) {
            attachedSide?.foreground = null
            side.foreground = scrimDrawable
            attachedSide = side
        }
        // progress=0 → 最不透明；progress=1 → 完全透明
        val alpha = (maxAlpha * (1f - progress) * 255f).toInt().coerceIn(0, 255)
        scrimDrawable.color = (alpha shl 24) or (baseR shl 16) or (baseG shl 8) or baseB
    }
}
