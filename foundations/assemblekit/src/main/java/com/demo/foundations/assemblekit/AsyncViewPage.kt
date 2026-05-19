package com.demo.foundations.assemblekit

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.asynclayoutinflater.view.AsyncLayoutInflater
import com.demo.thirdparty.logger.Logger

/**
 * 一个特殊的 [ViewPage]：通过 [AsyncLayoutInflater] 在后台线程把 XML 布局 inflate 出来，
 * 完成后把真正的 view 塞进一个 placeholder 里。
 *
 * ## 什么时候用
 *
 * 普通的 [ViewPage] 在 [Page.performAttach] 里**同步**在主线程 inflate 布局。
 * 对大多数 Page（一条头部 bar、30 行的表单、一个 footer）这没问题，继续用 [ViewPage] 就行。
 * 但如果某个 Page 的根布局确实重（深 view 树、巨大的 `ConstraintLayout`、一堆 `merge`
 * include、初始化代价高的自定义 view），同步 inflate 的开销就会叠加：
 * `assemble { +A; +B; +C }` 会在主线程上串行地把 A + B + C 的 inflate 时间累加起来。
 * 如果这把首帧预算吃穿了，[AsyncViewPage] 就是那把手术刀。
 *
 * 只在你**真正测出**首帧回归、并把锅扣到了布局 inflate 头上时才用它。
 * 异步 inflate 的本质是：用本 Page 自身首屏可见的延迟（placeholder 立即出现，
 * 但里面空白约几十毫秒）换主线程不被堵住，保证兄弟 Page、动画、输入响应仍然顺滑。
 * 如果你的页面本就是"折叠以下"或"次要槽位"，这通常划算；如果是首屏 hero 卡，
 * 通常就不划算。
 *
 * ## 与 [ViewPage] 相同的地方
 *
 *  - 生命周期接线、`SavedStateRegistry`、Mavericks 集成、scoped bus、scoped locals
 *    全部一致。你在 [onViewInflated] 里看到的 `PageContext` 跟普通 `ViewPage`
 *    在 `onViewCreated` 里看到的是同一个。
 *  - View 树 `PageContext` 戳。框架在 attach 时给 placeholder 打戳，等真正的 view
 *    inflate 完落地后再补一次戳；所以无论 inflate 是否已经完成，
 *    [View.findPageContext] 从任意后代节点都能正常工作。
 *
 * ## 不一样的地方
 *
 *  - 你要 override 的钩子是 [onViewInflated]，不是 `onViewCreated`。
 *    它在布局已经 inflate 完、并加入 placeholder **之后**才跑——这可能在
 *    `performAttach` 返回后好几帧才发生。任何需要操作真正 view 的代码
 *    （`findViewById`、view binding、click listener）都放这里。
 *  - 如果 Page 在 inflate 完成之前就被 detach 了（宿主在 finishing，或者
 *    [Assembly.replace] 把这个 Page 换掉了），晚到的完成回调会被丢弃，
 *    [onViewInflated] 不会被调用。
 *  - 在 [onViewInflated] 跑之前，对 Page 根 view 调用 `view.findViewById`
 *    会返回 `null`。不要在 `onCreate` / `onPageEvent` handler 这种可能比
 *    inflate 完成更早触发的地方同步读 view 状态；把这种读操作改为依赖
 *    一个状态字段。
 *
 * ## 示例
 *
 * ```kotlin
 * internal class HeavyDetailPage : AsyncViewPage(R.layout.page_heavy_detail) {
 *     private val viewModel: HeavyDetailViewModel by pageViewModel()
 *
 *     override fun onViewInflated(view: View) {
 *         val binding = PageHeavyDetailBinding.bind(view)
 *         viewModel.onEach(HeavyDetailState::title) { binding.title.text = it }
 *     }
 * }
 * ```
 *
 * @param layoutResId 要异步 inflate 的 XML 布局。
 * @param placeholderHeightPx inflate 进行中时 placeholder 的固定高度（可选）。
 *   默认 `WRAP_CONTENT`，真 view 落地时可能产生轻微的布局跳变。当你知道最终高度
 *   （比如一个固定大小的卡片槽）时建议显式给值，避免相邻 Page 抖动。
 */
abstract class AsyncViewPage(
    @LayoutRes private val layoutResId: Int,
    private val placeholderHeightPx: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    explicitId: String? = null,
) : ViewPage(explicitId = explicitId) {

    private var placeholder: FrameLayout? = null
    private var realView: View? = null

    /**
     * 当异步 inflate 完成、真正的 view 已经加进 placeholder 之后调用一次。
     * 语义等同于 [ViewPage.onViewCreated]，只是被推迟到布局真正就绪。
     *
     * 如果 Page 在 inflate 完成之前就被 detach 了，此方法**不会**被调用。
     */
    protected abstract fun onViewInflated(view: View)

    /**
     * 释放 [onViewInflated] 里分配的资源（可选）。
     * 由 [ViewPage.onDestroyView] 触发；placeholder / 真 view 的引用框架会替你清掉。
     */
    protected open fun onAsyncDestroyView() = Unit

    // ---- 与 ViewPage 的衔接 -------------------------------------------

    final override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View {
        val ph = FrameLayout(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                placeholderHeightPx,
            )
        }
        placeholder = ph

        val capturedCtx: PageContext = context
        AsyncLayoutInflater(parent.context).inflate(layoutResId, ph) { inflated, _, _ ->
            // inflate 进行中 Page 可能已经被 detach 了（宿主 finishing、
            // Assembly.replace 跑过等）。`placeholder` 在 onDestroyView 里
            // 被置空，所以拿它当哨兵判断。
            val target = placeholder
            if (target == null) {
                Logger.w(LOG_TAG, "Async inflate completed after detach for $pageId; dropping result")
                return@inflate
            }
            realView = inflated
            // 给 inflate 出来的 view 补一次戳，戳的是框架已经盖在 placeholder 上
            // 的同一个 PageContext。其实 placeholder 上的戳已经能让后代通过
            // `findPageContext()` 沿父链解析到，但是给 inflate 后的根再补一份，
            // 可以让某些把这个 subtree 单拎出去看的外部工具（截图、无障碍辅助）
            // 也能正确拿到 context。
            inflated.setPageContext(capturedCtx)
            target.addView(inflated)
            try {
                onViewInflated(inflated)
            } catch (t: Throwable) {
                Logger.w(LOG_TAG, "onViewInflated threw for $pageId: ${t.message}")
            }
        }
        return ph
    }

    /**
     * 封死的原因：异步 Page 用 [onViewInflated] 来表达 "view 就绪" 的时机，
     * 而不是同步的 [ViewPage.onViewCreated]（后者只会看到空的 placeholder）。
     */
    final override fun onViewCreated(view: View) {
        // 有意为之的空实现——见类级文档
    }

    final override fun onDestroyView() {
        realView?.setPageContext(null)
        realView = null
        placeholder = null
        try {
            onAsyncDestroyView()
        } catch (t: Throwable) {
            Logger.w(LOG_TAG, "onAsyncDestroyView threw for $pageId: ${t.message}")
        }
    }

    companion object {
        private const val LOG_TAG = "AsyncViewPage"
    }
}
