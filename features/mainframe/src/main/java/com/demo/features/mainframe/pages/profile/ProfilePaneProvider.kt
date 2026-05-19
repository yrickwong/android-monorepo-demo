package com.demo.features.mainframe.pages.profile

import androidx.fragment.app.Fragment
import com.demo.foundations.slidepane.PaneSlot
import com.demo.foundations.slidepane.spi.PaneProvider

/**
 * 个人页 Provider，注册到左侧（START）槽位。
 * RTL 场景由框架统一镜像，业务无需关心 LEFT/RIGHT。
 */
class ProfilePaneProvider : PaneProvider {
    override val slot: PaneSlot = PaneSlot.START
    override val paneId: String = "profile"
    override fun createFragment(): Fragment = ProfilePaneHostFragment()
}
