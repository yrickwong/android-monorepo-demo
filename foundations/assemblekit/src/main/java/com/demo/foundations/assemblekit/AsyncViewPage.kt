package com.demo.foundations.assemblekit

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.asynclayoutinflater.view.AsyncLayoutInflater
import com.demo.thirdparty.logger.Logger

/**
 * A [ViewPage] that inflates its XML layout on a background thread
 * via [AsyncLayoutInflater] and swaps the real view into a placeholder
 * once inflation finishes.
 *
 * ## When to use this
 *
 * A normal [ViewPage] inflates its layout **synchronously** on the main
 * thread inside [Page.performAttach]. For most pages — a header bar, a
 * 30-line form, a footer — that's fine and you should keep using
 * [ViewPage]. But for pages whose root layout is genuinely heavy
 * (deep view trees, large `ConstraintLayout`s, many `merge` includes,
 * costly custom-view init), the synchronous inflate cost stacks up:
 * `assemble { +A; +B; +C }` adds up A + B + C inflate cost serially on
 * the main thread. If that pushes the screen past its first-frame
 * budget, [AsyncViewPage] is the surgical fix.
 *
 * Pick this **only** when you have measured a real first-frame
 * regression and traced it back to layout inflation. Async inflate
 * trades latency-to-first-paint of the page itself (the placeholder
 * shows immediately but is empty for ~tens of milliseconds) for
 * unblocking the main thread so sibling Pages, animations, and input
 * stay smooth. If your page is already the "below-the-fold" or
 * "secondary slot" content, this is usually a win; if it's the
 * above-the-fold hero, it usually isn't.
 *
 * ## What's identical to [ViewPage]
 *
 *  - Lifecycle hookup, `SavedStateRegistry`, Mavericks integration,
 *    scoped buses, scoped locals — all unchanged. The `PageContext`
 *    you get in [onViewInflated] is exactly the same one a regular
 *    `ViewPage` sees in `onViewCreated`.
 *  - View-tree `PageContext` stamping. The framework stamps the
 *    placeholder at attach time and re-stamps the real inflated view
 *    once it lands, so [View.findPageContext] keeps working from any
 *    descendant regardless of whether inflation has finished yet.
 *
 * ## What's different
 *
 *  - The hook you override is [onViewInflated], not `onViewCreated`.
 *    It runs **after** the layout has been inflated and added to the
 *    placeholder, which may be several frames after `performAttach`
 *    returns. Anything that needs the real view (`findViewById`,
 *    view bindings, click listeners) belongs here.
 *  - If the Page is detached before inflation completes (e.g. the
 *    host is finishing, or [Assembly.replace] swapped this Page out),
 *    the late completion callback is dropped and [onViewInflated] is
 *    not invoked.
 *  - `view.findViewById` on the page root *before* [onViewInflated]
 *    runs returns `null`. Don't synchronously read view state from
 *    `onCreate` / `onPageEvent` handlers that fire before the
 *    inflation completes; gate them on a state field instead.
 *
 * ## Example
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
 * @param layoutResId The XML layout to inflate asynchronously.
 * @param placeholderHeightPx Optional fixed height for the placeholder
 *   while inflate is in flight. Defaults to `WRAP_CONTENT`, which can
 *   cause a small layout jump when the real view lands. Set this when
 *   you know the eventual height (e.g. a fixed-size card slot) to
 *   keep neighbouring Pages from shifting.
 */
abstract class AsyncViewPage(
    @LayoutRes private val layoutResId: Int,
    private val placeholderHeightPx: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    explicitId: String? = null,
) : ViewPage(explicitId = explicitId) {

    private var placeholder: FrameLayout? = null
    private var realView: View? = null

    /**
     * Called once the asynchronous inflate has completed and the real
     * view has been added to the placeholder. Equivalent to
     * [ViewPage.onViewCreated] but deferred until the layout is ready.
     *
     * If the Page is detached before the inflate finishes, this is
     * never called.
     */
    protected abstract fun onViewInflated(view: View)

    /**
     * Optional teardown for resources allocated in [onViewInflated].
     * Runs from [ViewPage.onDestroyView]; the framework still drops
     * the placeholder / real-view references for you.
     */
    protected open fun onAsyncDestroyView() = Unit

    // ---- ViewPage glue ------------------------------------------------

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
            // The Page may have been detached while inflate was in flight
            // (host finishing, Assembly.replace ran, etc.). `placeholder`
            // is nulled in onDestroyView, so use it as the canary.
            val target = placeholder
            if (target == null) {
                Logger.w(LOG_TAG, "Async inflate completed after detach for $pageId; dropping result")
                return@inflate
            }
            realView = inflated
            // Re-stamp the inflated view with the same PageContext the
            // framework put on the placeholder. The placeholder's stamp
            // already lets descendants resolve via `findPageContext()`
            // through parent-chain walk, but stamping the inflated root
            // too means an external screenshot / accessibility helper
            // that picks up the inflated subtree in isolation still
            // sees the correct context.
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
     * Sealed because async pages express their "view is ready" moment
     * via [onViewInflated], not the synchronous [ViewPage.onViewCreated]
     * (which only sees the empty placeholder).
     */
    final override fun onViewCreated(view: View) {
        // intentional no-op — see class doc
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
