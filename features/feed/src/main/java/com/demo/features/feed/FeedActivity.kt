package com.demo.features.feed

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.demo.features.feed.data.FeedRepository
import com.demo.features.feed.databinding.FeedActivityBinding
import com.demo.features.feed.page.FeedBannerPage
import com.demo.features.feed.page.FeedFooterPage
import com.demo.features.feed.page.FeedHeaderPage
import com.demo.features.feed.page.FeedListPage
import com.demo.foundations.analytics.Analytics
import com.demo.foundations.assemblekit.Assembly
import com.demo.foundations.assemblekit.PageHostActivity
import com.demo.foundations.assemblekit.assemble
import com.demo.foundations.ui.Toaster

/**
 * Single-screen showcase for the v2 features of AssembleKit:
 *
 *  - **provides / consume** — a [FeedRepository] and a click handler
 *    are pushed into [com.demo.foundations.assemblekit.Assembly]'s
 *    locals; the list rows pull them out via
 *    `requireConsume(FeedRepositoryKey)` without taking either as a
 *    constructor parameter.
 *  - **multi-slot layout with `at(R.id.…)`** — the host layout has
 *    three named slots and each Page pins itself to the slot it wants.
 *    `assemble {}` is called with `container = null`, so any Page that
 *    forgets to call `at(...)` would fail loud at install time.
 *  - **`Assembly.replace { }`** — pressing "toggle banner" reshuffles
 *    the composition (with/without an extra [FeedBannerPage]) without
 *    losing the rest of the screen's state.
 *  - **[com.demo.foundations.assemblekit.list.ListPage]** — the feed
 *    body is a stock framework primitive, not a hand-rolled
 *    RecyclerView setup.
 */
class FeedActivity : PageHostActivity() {

    private lateinit var binding: FeedActivityBinding
    private lateinit var repository: FeedRepository
    private lateinit var feedAssembly: Assembly

    /**
     * Tracks which composition variant is currently mounted so the
     * "toggle banner" button can flip back and forth.
     */
    private var bannerVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = FeedActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Analytics.logPageView("feed")

        repository = FeedRepository()

        // The host owns "what is on screen". Provide the repository
        // *and* the click handler at assembly scope; both the list
        // rows and the footer Page will consume them transparently.
        feedAssembly = assemble {
            provides(FeedRepositoryKey, repository)
            provides(NoteClickKey) { note ->
                Analytics.logEvent("feed_item_click", mapOf("note" to note.id))
                repository.likeOne(note.id)
                Toaster.short(this@FeedActivity, "Liked: ${note.title}")
            }

            +FeedHeaderPage() at R.id.feed_header_slot
            +FeedListPage(repository.notes) at R.id.feed_body_slot
            +FeedFooterPage() at R.id.feed_footer_slot
        }

        // Host-level concerns: react to the two events the Pages emit.
        // The Pages have no way to call assembly.replace() themselves —
        // that's a host-only API on purpose. They only ASK; the host
        // decides.
        hostBus.on<FeedEvent.RefreshRequested>(lifecycleScope) {
            repository.refresh()
            Toaster.short(this@FeedActivity, "Refreshed")
        }

        hostBus.on<FeedEvent.ToggleBannerRequested>(lifecycleScope) {
            bannerVisible = !bannerVisible
            recomposeFeed()
        }
    }

    /**
     * Rebuild the same Assembly with a different page list. Pages that
     * were already attached are detached cleanly (their host-lifecycle
     * observer is removed in `Page.performDetach`), then the new
     * composition is materialised. The `provides(...)` calls are
     * re-issued because `Assembly.replace` wipes assembly-scope locals
     * for predictability — anything the host wired on hostLocal would
     * survive instead.
     */
    private fun recomposeFeed() {
        feedAssembly.replace {
            provides(FeedRepositoryKey, repository)
            provides(NoteClickKey) { note ->
                Analytics.logEvent("feed_item_click", mapOf("note" to note.id))
                repository.likeOne(note.id)
                Toaster.short(this@FeedActivity, "Liked: ${note.title}")
            }

            +FeedHeaderPage() at R.id.feed_header_slot
            if (bannerVisible) {
                +FeedBannerPage() at R.id.feed_body_slot
            }
            +FeedListPage(repository.notes) at R.id.feed_body_slot
            +FeedFooterPage() at R.id.feed_footer_slot
        }
    }
}
