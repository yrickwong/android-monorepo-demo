package com.demo.features.mainframe.pages.messages

import androidx.fragment.app.Fragment
import com.demo.foundations.slidepane.PaneSlot
import com.demo.foundations.slidepane.spi.PaneProvider

/**
 * 消息页 Provider，注册到右侧（END）槽位。
 */
class MessagesPaneProvider : PaneProvider {
    override val slot: PaneSlot = PaneSlot.END
    override val paneId: String = "messages"
    override fun createFragment(): Fragment = MessagesPaneHostFragment()
}
