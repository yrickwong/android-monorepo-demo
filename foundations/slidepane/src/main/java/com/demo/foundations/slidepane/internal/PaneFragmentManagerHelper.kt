package com.demo.foundations.slidepane.internal

import androidx.annotation.IdRes
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import com.demo.foundations.slidepane.PaneSlot
import com.demo.foundations.slidepane.spi.PaneProvider

/**
 * 封装 FragmentManager 的 attach / setMaxLifecycle 操作。
 *
 * 几亿 DAU 场景下，必须用 [androidx.fragment.app.FragmentTransaction.setMaxLifecycle]
 * 精细控制可见性，避免不可见 Pane 仍占用主线程做动画/网络。
 */
internal class PaneFragmentManagerHelper(
    private val fragmentManager: FragmentManager
) {

    /**
     * 确保某个 Pane 已 attach。已存在则返回现有 Fragment。
     */
    fun ensureAttached(
        provider: PaneProvider,
        @IdRes containerId: Int
    ): androidx.fragment.app.Fragment {
        val tag = tagOf(provider.slot, provider.paneId)
        fragmentManager.findFragmentByTag(tag)?.let { return it }

        val fragment = provider.createFragment()
        fragmentManager.beginTransaction()
            .add(containerId, fragment, tag)
            .setMaxLifecycle(fragment, Lifecycle.State.STARTED)
            .commitNow()
        return fragment
    }

    /**
     * 将指定 Pane 提升到 RESUMED（完全可见时调用）
     */
    fun moveToResumed(slot: PaneSlot, paneId: String) {
        val fragment = fragmentManager.findFragmentByTag(tagOf(slot, paneId)) ?: return
        fragmentManager.beginTransaction()
            .setMaxLifecycle(fragment, Lifecycle.State.RESUMED)
            .commitNow()
    }

    /**
     * 将指定 Pane 降到 STARTED（不再可见时调用）
     */
    fun moveToStarted(slot: PaneSlot, paneId: String) {
        val fragment = fragmentManager.findFragmentByTag(tagOf(slot, paneId)) ?: return
        fragmentManager.beginTransaction()
            .setMaxLifecycle(fragment, Lifecycle.State.STARTED)
            .commitNow()
    }

    private fun tagOf(slot: PaneSlot, paneId: String) = "slidepane:${slot.name}:$paneId"
}
