package com.demo.foundations.slidepane.strategy

import com.demo.foundations.slidepane.PaneApi
import com.demo.foundations.slidepane.PaneSlot

/**
 * Pane 加载策略，决定 Fragment 何时被 attach。
 */
@PaneApi
interface PaneLoadingStrategy {

    /**
     * @return true 表示该槽位的 Fragment 在 bind 阶段就 attach；
     *         false 表示首次滑动触发时再懒加载。
     */
    @PaneApi
    fun shouldPreload(slot: PaneSlot): Boolean

    companion object {
        /** 默认：三个 Pane 全部预加载（几亿 DAU 主流场景，内存换流畅） */
        @PaneApi
        @JvmField
        val Eager: PaneLoadingStrategy = object : PaneLoadingStrategy {
            override fun shouldPreload(slot: PaneSlot): Boolean = true
        }

        /** 懒加载：仅 CENTER 预加载，侧 Pane 首次触发时加载 */
        @PaneApi
        @JvmField
        val Lazy: PaneLoadingStrategy = object : PaneLoadingStrategy {
            override fun shouldPreload(slot: PaneSlot): Boolean = slot == PaneSlot.CENTER
        }
    }
}
