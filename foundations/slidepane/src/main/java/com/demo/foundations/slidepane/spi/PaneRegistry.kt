package com.demo.foundations.slidepane.spi

import androidx.annotation.MainThread
import com.demo.foundations.slidepane.PaneApi
import com.demo.foundations.slidepane.PaneSlot

/**
 * Pane 注册中心。
 *
 * - 业务方在 Application 启动或模块初始化阶段调用 [register]
 * - 同一槽位重复注册时，后注册者覆盖前者（便于实验切换）
 * - [SlidePaneContainer.bind] 时从此处读取
 *
 * 默认提供单例 [Default]，业务也可创建独立实例做局部隔离（如多入口主框架）。
 */
@PaneApi
class PaneRegistry {

    private val providers = LinkedHashMap<PaneSlot, PaneProvider>()
    private val listeners = mutableListOf<OnRegistryChangeListener>()

    @MainThread
    @PaneApi
    fun register(provider: PaneProvider) {
        if (!provider.isEnabled()) return
        providers[provider.slot] = provider
        listeners.toList().forEach { it.onRegistryChanged(provider.slot) }
    }

    @MainThread
    @PaneApi
    fun unregister(slot: PaneSlot) {
        if (providers.remove(slot) != null) {
            listeners.toList().forEach { it.onRegistryChanged(slot) }
        }
    }

    @PaneApi
    fun get(slot: PaneSlot): PaneProvider? = providers[slot]

    @PaneApi
    fun all(): Map<PaneSlot, PaneProvider> = providers.toMap()

    @PaneApi
    fun addOnChangeListener(listener: OnRegistryChangeListener) {
        listeners.add(listener)
    }

    @PaneApi
    fun removeOnChangeListener(listener: OnRegistryChangeListener) {
        listeners.remove(listener)
    }

    @PaneApi
    fun interface OnRegistryChangeListener {
        fun onRegistryChanged(slot: PaneSlot)
    }

    companion object {
        /** 全局默认注册中心 */
        @PaneApi
        @JvmField
        val Default: PaneRegistry = PaneRegistry()
    }
}
