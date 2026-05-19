package com.demo.features.mainframe

import com.demo.features.mainframe.pages.home.HomePaneProvider
import com.demo.features.mainframe.pages.messages.MessagesPaneProvider
import com.demo.features.mainframe.pages.profile.ProfilePaneProvider
import com.demo.foundations.slidepane.spi.PaneRegistry

/**
 * 业务 Pane 统一注册入口。
 *
 * 用法（在宿主 Application#onCreate 中调用一次）：
 * ```kotlin
 * override fun onCreate() {
 *     super.onCreate()
 *     MainframePaneRegistration.registerAll()
 * }
 * ```
 *
 * 之后 [MainActivity] 启动时，[com.demo.foundations.slidepane.SlidePaneContainer]
 * 会通过 [PaneRegistry] 拿到 3 个 Provider，按 slot 自动调度到
 * CENTER / START / END 三个位置。
 *
 * 如果以后需要远端开关 / A-B 实验，只在这里做条件性注册即可——MainActivity
 * 与 SlidePane 框架完全不感知业务。
 */
object MainframePaneRegistration {

    fun registerAll(registry: PaneRegistry = PaneRegistry.Default) {
        registry.register(HomePaneProvider())
        registry.register(ProfilePaneProvider())
        registry.register(MessagesPaneProvider())
    }
}
