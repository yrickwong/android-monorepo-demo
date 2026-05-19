package com.demo.features.mainframe.pages.profile.binders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.demo.features.mainframe.R
import com.demo.features.mainframe.data.MockRepository
import com.demo.foundations.assemblekit.PageContext
import com.demo.foundations.assemblekit.list.ItemBinder

/**
 * 个人页顶部信息卡 binder（头像 / 昵称 / handle / 简介 / 数据栏 / Notes-Collections 切换）。
 *
 * 与 [com.demo.features.mainframe.pages.home.binders.HomeNoteBinder] 一起塞进
 * [com.demo.features.mainframe.pages.profile.ProfileContentPage] 的内部多类型 adapter；
 * 它本身只负责"画"——所有数据由外层 Page 从 Shell VM 拿到后传进来。
 *
 * 占满两列（StaggeredGridLayoutManager 的 isFullSpan）由 Page 的内部 adapter
 * 在 `onViewAttachedToWindow` 里设置——参考原 ProfileAdapter 的实现方式。把这件
 * 事留在 Page 而不是 binder 内，是因为 binder.bind() 触发时 itemView 的 layoutParams
 * 还可能是默认 ViewGroup.LayoutParams（RV 在 attach 时才会包装成 LayoutManager
 * 专用 LayoutParams），在 bind 里 cast 会失败。
 */
internal class ProfileHeaderBinder : ItemBinder<MockRepository.UserProfile> {

    override fun createView(parent: ViewGroup, ctx: PageContext): View =
        LayoutInflater.from(parent.context)
            .inflate(R.layout.mainframe_item_profile_header, parent, false)

    override fun bind(
        view: View,
        item: MockRepository.UserProfile,
        position: Int,
        ctx: PageContext,
    ) {
        val avatar = view.findViewById<View>(R.id.mainframe_profile_avatar)
        val name = view.findViewById<TextView>(R.id.mainframe_profile_name)
        val handle = view.findViewById<TextView>(R.id.mainframe_profile_handle)
        val bio = view.findViewById<TextView>(R.id.mainframe_profile_bio)
        val cntFollowing = view.findViewById<TextView>(R.id.mainframe_profile_cnt_following)
        val cntFollowers = view.findViewById<TextView>(R.id.mainframe_profile_cnt_followers)
        val cntLikes = view.findViewById<TextView>(R.id.mainframe_profile_cnt_likes)

        avatar.setBackgroundColor(0xFFFFB3C1.toInt())
        name.text = item.name
        handle.text = "${item.handle} · 📍${item.location}"
        bio.text = item.bio
        cntFollowing.text = formatCount(item.following)
        cntFollowers.text = formatCount(item.followers)
        cntLikes.text = formatCount(item.likesAndSaves)
    }

    override fun areItemsTheSame(
        old: MockRepository.UserProfile,
        new: MockRepository.UserProfile,
    ): Boolean = old.handle == new.handle

    override fun areContentsTheSame(
        old: MockRepository.UserProfile,
        new: MockRepository.UserProfile,
    ): Boolean = old == new

    private fun formatCount(value: Int): String = when {
        value >= 10000 -> String.format("%.1fK", value / 1000f)
        else -> value.toString()
    }
}
