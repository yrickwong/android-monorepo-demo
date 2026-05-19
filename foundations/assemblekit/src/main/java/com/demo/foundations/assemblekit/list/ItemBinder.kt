package com.demo.foundations.assemblekit.list

import android.view.View
import android.view.ViewGroup
import com.demo.foundations.assemblekit.PageContext

/**
 * [ListPage] 内单个 row 的渲染契约。
 *
 * `ItemBinder<T>` 故意**不是**另一个 [com.demo.foundations.assemblekit.Page]：
 * 一个上千行的 feed 不应该为之付出上千个 lifecycle owner、上千个 Mavericks ViewModel
 * 或者上千条 scoped event bus 的代价。
 *
 * 相反，row 是 "context 透明" 的：它们共享所属 [ListPage] 的 [PageContext]，
 * 也就是说外层屏幕 provide 过的任何东西——repository、点击事件桥、主题 token——
 * 它们都可以通过 [PageContext.consume] / [PageContext.requireConsume] 直接拿到，
 * 调用方不必把参数一层层往下钻。
 *
 * 典型用法：
 * ```kotlin
 * val NoteRepoKey = pageContextKey<NoteRepository>("note-repo")
 *
 * class NoteItemBinder : ItemBinder<Note> {
 *     override fun createView(parent: ViewGroup, ctx: PageContext): View =
 *         FeedItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false).root
 *
 *     override fun bind(view: View, item: Note, position: Int, ctx: PageContext) {
 *         val repo = ctx.requireConsume(NoteRepoKey)
 *         view.findViewById<TextView>(R.id.title).text = item.title
 *         view.setOnClickListener { repo.markRead(item.id) }
 *     }
 * }
 * ```
 *
 * 实现应当是 **无状态** 的——任何 per-row 的状态都应该放在 [T] 本身里，
 * 或者放在通过 context 暴露的 repository 里。框架会让同一个 [ItemBinder]
 * 实例跨多个 position、跨多次 [ListPage] 重绑复用；不要在 binder 上缓存 view 引用。
 */
interface ItemBinder<T> {

    /**
     * inflate / 构建 row [View]。每个 RecyclerView viewHolder 只会被调一次；
     * 框架会缓存返回值。
     *
     * 不要把 view attach 到 [parent] 上；框架会用 ViewHolder 包一层，
     * 然后让 RecyclerView 自己去管理 attach。
     */
    fun createView(parent: ViewGroup, ctx: PageContext): View

    /**
     * 把 [item] 数据绑定到 [view] 上。每次 row 展示或底层 item 变化时都会被调。
     * [position] 是绑定时的 adapter position——不要缓存。
     */
    fun bind(view: View, item: T, position: Int, ctx: PageContext)

    /**
     * row 永久离场（RecyclerView 的 `onViewRecycled`）时的可选清理。
     * 默认空实现足以覆盖 binder 只设 text / image 的常见场景。
     */
    fun unbind(view: View) = Unit

    /**
     * DiffUtil 钩子：两条数据是不是*同一行*？默认是 `==`，当 [T] 有稳定身份
     * （比如按 id 的 data class）时是正确的。如果想表达 "同一个实体的两个快照"，
     * 请覆写。
     */
    fun areItemsTheSame(old: T, new: T): Boolean = old == new

    /**
     * DiffUtil 钩子：两份快照渲染出来是不是一样？默认是 `==`。
     * 当 [T] 带有 row 并不展示的字段、又想跳过不必要的重绑时请覆写。
     */
    fun areContentsTheSame(old: T, new: T): Boolean = old == new
}
