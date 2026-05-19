package com.demo.features.mainframe.pages.home.binders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.demo.features.mainframe.R
import com.demo.features.mainframe.data.MockRepository
import com.demo.foundations.assemblekit.PageContext
import com.demo.foundations.assemblekit.list.ItemBinder

/**
 * 首页瀑布流卡片 binder（也被 Profile 个人页复用）。
 *
 * 与原 [com.demo.features.mainframe.pages.home.HomeFeedPage] 的协作：
 *  - 卡片图片的高度由 [columnWidth] × [MockRepository.FeedNote.aspectRatio] 算出。
 *    [columnWidth] 由外部通过构造参数传入——避免每个 binder 自己量列宽。
 *  - 不依赖 ShellVM：本 binder 只渲染数据，不触发任何业务动作；点击交互如有需要
 *    可在 view.setOnClickListener 里通过 ctx 拿 VM 做。
 *
 * 列宽用 lateinit 风格的可变属性而不是构造参数，是因为 binder 是 object 单例。
 * 这样 [com.demo.features.mainframe.pages.home.HomeFeedPage] 算完列宽后调用
 * [setColumnWidth] 一次即可（同 [com.demo.features.mainframe.pages.profile.ProfileContentPage]）。
 */
internal class HomeNoteBinder : ItemBinder<MockRepository.FeedNote> {

    private var columnWidthPx: Int = 0

    fun setColumnWidth(widthPx: Int) {
        columnWidthPx = widthPx
    }

    override fun createView(parent: ViewGroup, ctx: PageContext): View =
        LayoutInflater.from(parent.context)
            .inflate(R.layout.mainframe_item_home_note, parent, false)

    override fun bind(view: View, item: MockRepository.FeedNote, position: Int, ctx: PageContext) {
        val coverWrapper = view.findViewById<View>(R.id.mainframe_note_cover_wrapper)
        val cover = view.findViewById<View>(R.id.mainframe_note_cover)
        val tag = view.findViewById<TextView>(R.id.mainframe_note_tag)
        val title = view.findViewById<TextView>(R.id.mainframe_note_title)
        val avatar = view.findViewById<View>(R.id.mainframe_note_avatar)
        val author = view.findViewById<TextView>(R.id.mainframe_note_author)
        val likes = view.findViewById<TextView>(R.id.mainframe_note_likes)

        // 动态高度（仅在列宽已知时设置）
        if (columnWidthPx > 0) {
            val target = (columnWidthPx * item.aspectRatio).toInt()
            val lp = coverWrapper.layoutParams
            if (lp.height != target) {
                lp.height = target
                coverWrapper.layoutParams = lp
            }
        }

        cover.setBackgroundColor(item.coverColor)
        tag.visibility = if (item.tag == null) View.GONE else View.VISIBLE
        tag.text = item.tag
        title.text = item.title
        avatar.setBackgroundColor(item.avatarColor)
        author.text = item.author
        likes.text = formatLikes(item.likes)
    }

    override fun areItemsTheSame(
        old: MockRepository.FeedNote,
        new: MockRepository.FeedNote,
    ): Boolean = old.id == new.id

    private fun formatLikes(value: Int): String =
        if (value >= 1000) String.format("%.1fk", value / 1000f) else value.toString()
}
