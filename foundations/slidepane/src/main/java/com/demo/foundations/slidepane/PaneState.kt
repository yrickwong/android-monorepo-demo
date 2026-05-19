package com.demo.foundations.slidepane

/**
 * 容器当前的稳定状态。
 *
 * 滑动过程中（手指拖动 / settle 动画中）的中间态不在此枚举内，
 * 实时进度通过 [com.demo.foundations.slidepane.SlideProgress] 暴露。
 */
@PaneApi
enum class PaneState {
    /** 主屏完全显示 */
    CENTER,

    /** START 侧 Pane 完全打开 */
    START_OPEN,

    /** END 侧 Pane 完全打开 */
    END_OPEN
}
