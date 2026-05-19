package com.demo.features.mainframe.pages.home.binders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.demo.features.mainframe.R
import com.demo.features.mainframe.state.HomeShellViewModelKey
import com.demo.features.mainframe.state.HomeTabRow
import com.demo.foundations.assemblekit.PageContext
import com.demo.foundations.assemblekit.list.ItemBinder

/**
 * 顶部分类 Tab 单 item 的 binder。
 *
 * 设计要点：
 *  - **stateless**: 不持有 selectedIndex 字段——选中态完全由 [HomeTabRow.selected]
 *    决定，DiffUtil 基于 data class equality 精确识别行变化。
 *  - 点击时通过 [PageContext.requireConsume] 拿到 HomeShellViewModel，调用
 *    `selectTab(index)`——VM 改 state → state 改 tabRows → ListPage 重新提交 →
 *    DiffUtil 仅重绘"旧选中"和"新选中"两行。
 *  - 不持有 view 引用：根据 AssembleKit 约定，binder 是单例可复用的。
 */
internal object HomeTabBinder : ItemBinder<HomeTabRow> {

    override fun createView(parent: ViewGroup, ctx: PageContext): View =
        LayoutInflater.from(parent.context)
            .inflate(R.layout.mainframe_item_home_tab, parent, false)

    override fun bind(view: View, item: HomeTabRow, position: Int, ctx: PageContext) {
        val text = view as TextView
        text.text = item.name
        text.isSelected = item.selected
        text.setOnClickListener {
            ctx.requireConsume(HomeShellViewModelKey).selectTab(item.index)
        }
    }

    override fun unbind(view: View) {
        view.setOnClickListener(null)
    }

    override fun areItemsTheSame(old: HomeTabRow, new: HomeTabRow): Boolean =
        old.index == new.index
}
