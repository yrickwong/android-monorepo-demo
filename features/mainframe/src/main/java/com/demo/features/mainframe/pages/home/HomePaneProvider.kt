package com.demo.features.mainframe.pages.home

import androidx.fragment.app.Fragment
import com.demo.foundations.slidepane.PaneSlot
import com.demo.foundations.slidepane.spi.PaneProvider

/**
 * 首页 Provider，注册到 [com.demo.foundations.slidepane.spi.PaneRegistry] 即接入框架。
 *
 * 注册之后 SlidePane 在 attach 时会调用 [createFragment] 创建
 * [HomePaneHostFragment]，并把它挂进 CENTER 槽位。
 */
class HomePaneProvider : PaneProvider {
    override val slot: PaneSlot = PaneSlot.CENTER
    override val paneId: String = "home"
    override fun createFragment(): Fragment = HomePaneHostFragment()
}
