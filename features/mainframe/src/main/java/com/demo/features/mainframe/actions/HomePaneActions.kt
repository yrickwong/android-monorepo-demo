package com.demo.features.mainframe.actions

import com.demo.foundations.slidepane.gesture.ScrollDirectionListener

/**
 * Home Pane 对外暴露的"业务请求"接口。
 *
 * Pages 不直接持有 [com.demo.foundations.slidepane.SlidePaneContainer]——它们通过 hostLocal
 * 拿到本接口的实现（MainActivity），把"打开个人页"/"打开消息"这种业务意图翻译为对
 * SlidePane API 的调用。Pages 与框架完全解耦。
 *
 * 设计要点：
 *  - 接口方法名都用 `request*` 前缀：表示"业务请求"，由实现方决定如何执行（比如可能
 *    被拦截 / 加埋点 / 弹确认弹窗）。
 *  - [addScrollDirectionListener] / [removeScrollDirectionListener] 把"滚动方向"
 *    这种纯 UI 信号通过框架的 [ScrollDirectionListener] 向下广播——Home 的底部悬浮栏
 *    会订阅它实现"上滑收起 / 下滑展开"动画。
 */
interface HomePaneActions {
    /** 请求打开个人页（START 槽位）。 */
    fun requestOpenProfile()

    /** 请求打开消息页（END 槽位）。 */
    fun requestOpenMessages()

    /** 订阅 Feed 的垂直滚动方向（由 Home 底部栏在 onAttachedToWindow 时调用）。 */
    fun addScrollDirectionListener(listener: ScrollDirectionListener)

    /** 取消订阅（由 Home 底部栏在 onDetachedFromWindow 时调用）。 */
    fun removeScrollDirectionListener(listener: ScrollDirectionListener)
}
