package com.demo.foundations.slidepane.spi

import androidx.fragment.app.Fragment
import com.demo.foundations.slidepane.PaneApi
import com.demo.foundations.slidepane.PaneSlot
import com.demo.foundations.slidepane.strategy.PaneTransitionAnimator

/**
 * 业务接入接口（SPI）。
 *
 * 业务方实现此接口并通过 [PaneRegistry.register] 注册，框架根据槽位调度。
 * 框架不感知任何业务概念，仅依赖此接口。
 *
 * 实现示例：
 * ```kotlin
 * class HomePaneProvider : PaneProvider {
 *     override val slot = PaneSlot.CENTER
 *     override val paneId = "home"
 *     override fun createFragment(): Fragment = HomePaneFragment()
 * }
 * ```
 */
@PaneApi
interface PaneProvider {

    /** 槽位标识 */
    @PaneApi
    val slot: PaneSlot

    /** 唯一 ID，便于埋点、状态恢复、A/B 实验切换。同一 slot 下 paneId 必须稳定 */
    @PaneApi
    val paneId: String

    /** 创建该 Pane 对应的 Fragment（懒加载，框架在需要时调用） */
    @PaneApi
    fun createFragment(): Fragment

    /** 该 Pane 的宽度策略 */
    @PaneApi
    fun widthSpec(): PaneWidthSpec = PaneWidthSpec.MatchParent

    /** 该 Pane 是否启用（远端开关 / 实验降级时可返回 false 跳过注册） */
    @PaneApi
    fun isEnabled(): Boolean = true

    /**
     * 业务自定义出场动效。返回 null 时使用容器全局配置的 [PaneTransitionAnimator]。
     * 该方法仅会在 Pane 被打开时调用，可针对单个 Pane 提供差异化动效。
     */
    @PaneApi
    fun customAnimator(): PaneTransitionAnimator? = null
}
