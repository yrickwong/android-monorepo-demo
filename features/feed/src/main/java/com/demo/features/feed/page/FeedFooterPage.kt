package com.demo.features.feed.page

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.demo.features.feed.FeedRepositoryKey
import com.demo.features.feed.R
import com.demo.features.feed.databinding.FeedPageFooterBinding
import com.demo.foundations.assemblekit.ViewPage
import kotlinx.coroutines.launch

/**
 * Counter strip pinned at `R.id.feed_footer_slot`. Demonstrates a Page
 * that *consumes* the same repository the list does, completely
 * separately, without any sibling-to-sibling wiring.
 *
 * It just calls `requireConsume(FeedRepositoryKey)` and gets whatever
 * the host or assembly provided — repo today, in-memory fake in a UI
 * test tomorrow, no edits required here.
 */
internal class FeedFooterPage : ViewPage() {

    private var binding: FeedPageFooterBinding? = null

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View {
        val b = FeedPageFooterBinding.inflate(inflater, parent, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View) {
        val repo = requireConsume(FeedRepositoryKey)
        val ctx = view.context
        pageScope.launch {
            repo.notes.collect { items ->
                binding?.footerText?.text =
                    ctx.getString(R.string.feed_footer_count, items.size)
            }
        }
    }

    override fun onDestroyView() {
        binding = null
    }
}
