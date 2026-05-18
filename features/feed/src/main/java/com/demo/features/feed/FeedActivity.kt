@file:OptIn(com.airbnb.mvrx.InternalMavericksApi::class)

package com.demo.features.feed

import android.os.Bundle
import com.airbnb.mvrx.ActivityViewModelContext
import com.airbnb.mvrx.MavericksView
import com.airbnb.mvrx.MavericksViewModelProvider
import com.demo.features.feed.databinding.FeedActivityBinding
import com.demo.features.feed.page.FeedBannerPage
import com.demo.features.feed.page.FeedFooterPage
import com.demo.features.feed.page.FeedHeaderPage
import com.demo.features.feed.page.FeedListPage
import com.demo.foundations.analytics.Analytics
import com.demo.foundations.assemblekit.Assembly
import com.demo.foundations.assemblekit.AssemblyBuilder
import com.demo.foundations.assemblekit.PageHostActivity
import com.demo.foundations.assemblekit.assemble
import com.demo.foundations.assemblekit.local.pageContextKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Feed host. Now the **only** stateful object in the screen is
 * [FeedShellViewModel]; this Activity's job is reduced to two things:
 *
 *  1. **Create the VM in Activity scope** (so it survives config
 *     changes) and `provides` it to every Page through the assembly's
 *     locals — no constructor wiring, no DI graph.
 *  2. **React to structural state** ([FeedShellState.showBanner]) by
 *     calling `assembly.replace { }`. This is the canonical pattern
 *     for v2 "host-driven recomposition": the rule "Pages can't reach
 *     the Assembly handle" is preserved because the trigger lives
 *     here, not in a Page.
 *
 * What we explicitly removed in the Mavericks migration:
 *  - The `hostBus.on<FeedEvent.*>` listeners — refresh and toggle are
 *    plain method calls on the VM now. The bus was only being used as
 *    a workaround for "Page can't see the Activity"; with a shared VM,
 *    Pages can call into shared behaviour directly.
 *  - The `bannerVisible: Boolean` field that mirrored what the user
 *    saw — that's now a property of the Mavericks state, so config
 *    changes and process death restoration get it right for free.
 *  - The duplicated `provides(...)` block inside [recomposeFeed] —
 *    we set up the locals once, in [installAssembly], and let
 *    `Assembly.replace { }`'s default behaviour re-inherit host locals.
 *
 * This Activity implements [MavericksView] purely so it can call
 * `viewModel.onEach(...)`; it has no [invalidate] body of its own and
 * doesn't render anything itself.
 */
class FeedActivity : PageHostActivity(), MavericksView {

    private lateinit var binding: FeedActivityBinding
    private lateinit var viewModel: FeedShellViewModel
    private lateinit var feedAssembly: Assembly

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = FeedActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Analytics.logPageView("feed")

        viewModel = createShellViewModel()
        installAssembly(showBanner = false)

        // The ONLY place where state drives structural recomposition.
        // Pages are forbidden from doing this themselves (they can't
        // see the Assembly handle), which is exactly what keeps the
        // "single host decides what's on screen" invariant tractable.
        viewModel.onEach(FeedShellState::showBanner) { showBanner ->
            // Skip the no-op call on initial subscription: the
            // composition we just installed already matches state.
            if (feedAssembly.matchesBannerState(showBanner)) return@onEach
            installAssembly(showBanner)
        }
    }

    /**
     * Mavericks' `MavericksView` contract. We subscribe via the
     * targeted `onEach` selectors above, so we never need to be told
     * "state changed, re-render everything" — and an empty
     * `invalidate` is correct, not a placeholder.
     */
    override fun invalidate() = Unit

    private fun createShellViewModel(): FeedShellViewModel =
        MavericksViewModelProvider.get(
            viewModelClass = FeedShellViewModel::class.java,
            stateClass = FeedShellState::class.java,
            // Activity scope: lives as long as this host, survives config
            // changes, dies with the host. Exactly the lifetime we want
            // for "the whole Feed screen".
            viewModelContext = ActivityViewModelContext(activity = this, args = null),
            key = "feed_shell",
        )

    /**
     * Build (or rebuild) the assembly with a layout that matches
     * [showBanner]. We tag the assembly with a sentinel local so
     * [matchesBannerState] can tell whether the next state change
     * actually requires re-mounting Pages — preventing infinite
     * onEach → replace → onEach cycles.
     */
    private fun installAssembly(showBanner: Boolean) {
        // The list's items flow is derived from VM state — projecting
        // the Async<List<Note>> onto a plain List<Note> (empty while
        // Uninitialized/Loading) and dropping duplicate frames so
        // DiffUtil doesn't re-run for unrelated state changes
        // (e.g. flipping `showBanner` would otherwise re-emit).
        val notesFlow = viewModel.stateFlow
            .map { it.noteList }
            .distinctUntilChanged()

        val build: AssemblyBuilder.() -> Unit = {
            provides(FeedShellViewModelKey, viewModel)
            provides(BannerStateKey, showBanner)

            +FeedHeaderPage() at R.id.feed_header_slot
            if (showBanner) {
                +FeedBannerPage() at R.id.feed_body_slot
            }
            +FeedListPage(notesFlow) at R.id.feed_body_slot
            +FeedFooterPage() at R.id.feed_footer_slot
        }

        if (::feedAssembly.isInitialized) {
            feedAssembly.replace(block = build)
        } else {
            feedAssembly = assemble(block = build)
        }
    }

    private fun Assembly.matchesBannerState(target: Boolean): Boolean =
        assemblyLocal.resolve(BannerStateKey) == target

    private companion object {
        /**
         * Marker local so we can detect "the composition already
         * matches this banner state" without keeping a parallel
         * field on the Activity (which would drift from
         * Mavericks state under config change restoration).
         */
        private val BannerStateKey =
            pageContextKey<Boolean>("feed.bannerVisible")
    }
}
