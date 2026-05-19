package com.demo.foundations.slidepane

/**
 * 容器配置。所有阈值均提供合理默认值。
 *
 * @property openVelocityThresholdDpPerSecond 触发 settle 到完全打开/关闭的速度阈值（dp/s）
 * @property openPositionThreshold 在无 fling 时，按位置百分比判定打开还是回中的阈值
 * @property edgeSlopDp 仅边缘可触发模式下，触发区距屏幕边缘的距离（dp）
 * @property enableParallax 是否启用视差效果
 * @property enableScrim 是否启用遮罩
 * @property enableEdgeOnly true 时仅屏幕边缘可触发滑动；false 时全屏任意位置可触发
 * @property settleDurationMs settle 动画基础时长，实际时长由 ViewDragHelper 根据距离/速度自适应
 */
@PaneApi
data class SlidePaneConfig(
    val openVelocityThresholdDpPerSecond: Float = 400f,
    val openPositionThreshold: Float = 0.5f,
    val edgeSlopDp: Float = 20f,
    val enableParallax: Boolean = true,
    val enableScrim: Boolean = true,
    val enableEdgeOnly: Boolean = false,
    val settleDurationMs: Int = 280
) {
    companion object {
        @PaneApi
        val Default: SlidePaneConfig = SlidePaneConfig()
    }
}
