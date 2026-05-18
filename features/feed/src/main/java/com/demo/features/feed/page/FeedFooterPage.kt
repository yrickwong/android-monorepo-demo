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
 * Renders **two** Mavericks slices:
 *  1. `notes: Async<List<Note>>` — status text (loading / count / error).
 *  2. `(lastShared, lastTag)` — proof that clicks 3-4 layers deep
 *     inside a row (in `NoteActionBar` / `RelatedTagsCarousel`'s inner
 *     ViewHolders) really did reach this same Shell ViewModel.
 *
 * Two reasons this Page is a clean view-tree demo witness:
 *  - It is mounted at a **different slot** from the list. If the deep
 *    widgets accidentally resolved a row-scoped or carousel-scoped
 *    VM, this Page would not see anything — the fact that the text
 *    here updates is the smoke test for "yes, the right Shell VM was
 *    found".
 *  - It uses two independent `onEach` selectors. Mavericks dedupes
 *    by reference per slice, so banner toggling does not redraw the
 *    counter and tag clicks do not redraw the count.
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
        val dash = ctx.getString(R.string.feed_dash)

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

        // Two-arg selector: re-fires only when either field changes.
        // This is the witness for "deep view → Shell VM" coordination:
        // clicks in NoteActionBar (depth 3) and tag chips (depth 5)
        // both surface here.
        viewModel.onEach(FeedShellState::lastShared, FeedShellState::lastTag) { shared, tag ->
            binding?.footerExtras?.text = ctx.getString(
                R.string.feed_footer_extras,
                shared ?: dash,
                tag ?: dash,
            )
        }
    }

    override fun onDestroyView() {
        binding = null
    }
}
