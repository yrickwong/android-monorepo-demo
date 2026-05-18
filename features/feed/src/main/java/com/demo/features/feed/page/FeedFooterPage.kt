package com.demo.features.feed.page

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.demo.features.feed.FeedShellState
import com.demo.features.feed.FeedShellViewModelKey
import com.demo.features.feed.R
import com.demo.features.feed.databinding.FeedPageFooterBinding
import com.demo.foundations.assemblekit.ViewPage

/**
 * Counter strip pinned at `R.id.feed_footer_slot`.
 *
 * Showcases the canonical "render a single state slice" pattern:
 *  - `consume` the shared shell ViewModel from the Assembly's locals.
 *  - Subscribe to *exactly the field we care about* with
 *    `viewModel.onEach(FeedShellState::notes)` — Mavericks only
 *    re-delivers when that slice changes, so unrelated state churn
 *    (e.g. flipping the banner) does not redraw the footer.
 *  - No raw `collect(repo.flow)` anywhere. The repository is not even
 *    in scope for this Page — that's the bug AGENTS.md Rule 2 was
 *    written to prevent.
 *
 * Loading / failure are surfaced too, so the footer can hint that a
 * refresh is in flight rather than freezing on the previous count.
 */
internal class FeedFooterPage : ViewPage() {

    private var binding: FeedPageFooterBinding? = null

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View {
        val b = FeedPageFooterBinding.inflate(inflater, parent, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View) {
        val viewModel = requireConsume(FeedShellViewModelKey)
        val ctx = view.context

        // Single subscription on the `notes: Async<List<Note>>` slice.
        // Mavericks dedupes by reference, so the footer is repainted
        // only when the Async itself transitions, not on banner flips.
        viewModel.onEach(FeedShellState::notes) { notes ->
            val items = notes() ?: emptyList()
            val label = when {
                notes is com.airbnb.mvrx.Loading -> ctx.getString(R.string.feed_footer_loading)
                notes is com.airbnb.mvrx.Fail -> ctx.getString(R.string.feed_footer_error)
                else -> ctx.getString(R.string.feed_footer_count, items.size)
            }
            binding?.footerText?.text = label
        }
    }

    override fun onDestroyView() {
        binding = null
    }
}
