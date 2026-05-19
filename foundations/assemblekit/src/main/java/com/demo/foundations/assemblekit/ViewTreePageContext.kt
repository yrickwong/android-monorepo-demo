package com.demo.foundations.assemblekit

import android.view.View

/**
 * "View 树 PageContext" helper——这是深层自定义 View 想拿到 assembly 共享状态
 * （通常是 Shell ViewModel）但又不想一路用参数往下传时缺的那块拼图。
 *
 * 心智模型：和 AndroidX 的 `ViewTreeLifecycleOwner` / `ViewTreeViewModelStoreOwner`
 * 一样，从语义上也等同于 Compose 的 `LocalComposition`。框架会把当前活跃的
 * [PageContext] 打到 Page 根 View 的 tag 上；任意子 View 都能顺着 `parent`
 * 链向上找，定位到最近一个被打过 tag 的 context。
 *
 * **谁来打 tag？**（你基本上不会手动调 [setPageContext]。）
 *  - [Page.performAttach] 会给 `materialize` 返回的那个 View 打 tag——
 *    不管是哪种 Page（[ViewPage]、[AsyncViewPage]，还是
 *    `:foundations:assemblekit-compose` 里的 `ComposablePage`），框架把它加进
 *    container 之后立刻就能用。
 *  - [com.demo.foundations.assemblekit.list.ListPage] 的内部 adapter 会给每个
 *    row 的 `itemView` 打 tag，这样行内深层子 View / 嵌套 RecyclerView 的
 *    ViewHolder 也能拿到所属 Page 的 context。
 *
 * **谁来读？** 任何需要调用 Shell ViewModel 的自定义 View。一个可复用 widget
 * 的典型用法：
 *
 * ```kotlin
 * class NoteActionBar(ctx: Context, attrs: AttributeSet?) : LinearLayout(ctx, attrs) {
 *     private var noteId: String? = null
 *     fun bind(noteId: String) { this.noteId = noteId }
 *
 *     init {
 *         likeButton.setOnClickListener {
 *             requirePageContext()
 *                 .requireConsume(FeedShellViewModelKey)
 *                 .likeOne(noteId ?: return@setOnClickListener)
 *         }
 *     }
 * }
 * ```
 *
 * 该 widget 只暴露一个字段级的 setter（`bind(noteId)`）——其他依赖
 * （ViewModel、埋点 tracker、主题 token）都通过 context 查找拿到。
 * 这个 widget 可以复用到*任何*提供了相同 key 的 AssembleKit 页面里；
 * 它的构造参数里不会出现特定业务类型。
 *
 * **为什么不用 DI 框架代替？** 用 DI 查找（`KoinJavaComponent.get<FeedShellViewModel>()`）
 * 机制上当然也能跑，但它会悄悄破坏 page-scoping 这个不变量——view 拿到的会是
 * *任意*一个活着的实例，而不一定是绑在 *本* 宿主上的那一个。详见
 * `docs/mvi-rules.md` 的 "Why not Koin/Hilt" 一节。
 *
 * **生命周期 / 安全性：** tag 是对 [PageContext] 的强引用，而 PageContext 又持有
 * 宿主。框架会在 detach 时清掉这个引用（见 [com.demo.foundations.assemblekit.Page.performDetach]
 * 与 [com.demo.foundations.assemblekit.list.ListPage.onDestroyView]），
 * 所以拆除之后 View 树不会把宿主继续吊住。
 */
fun View.setPageContext(context: PageContext?) {
    setTag(R.id.assemblekit_page_context_tag, context)
}

/**
 * 沿着 View 的 parent 链向上找，定位最近一个被打过 tag 的 [PageContext]。
 * 如果一路祖先都没有，返回 `null`——大多数时候这意味着 View 是被 `LayoutInflater`
 * 在任何 Page 之外实例化的（比如 preview、独立 dialog），调用方需要自己决定
 * 如何降级。
 */
fun View.findPageContext(): PageContext? {
    var current: View? = this
    while (current != null) {
        val tagged = current.getTag(R.id.assemblekit_page_context_tag)
        if (tagged is PageContext) return tagged
        current = current.parent as? View
    }
    return null
}

/**
 * 同 [findPageContext]，但当这个 View 找不到任何 PageContext 时直接抛异常。
 * 一些 widget 在设计上就*必须*挂在 AssembleKit 的 page 内部，给它们用这个方法——
 * 错误信息会让 "这个 widget 被用在了不支持的上下文里" 这种 bug 立刻暴露，
 * 而不是等到几帧之后冒出来一个 null deref。
 */
fun View.requirePageContext(): PageContext =
    findPageContext()
        ?: error(
            "No PageContext is reachable from this View. " +
                "View=${this.javaClass.simpleName}. " +
                "Make sure this View is mounted inside a Page (or a ListPage row); " +
                "directly inflated views (previews, raw dialogs) need to call " +
                "setPageContext(...) on the root before lookup works.",
        )
