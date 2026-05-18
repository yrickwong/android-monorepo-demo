package com.demo.features.feed.page

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.demo.features.feed.FeedShellViewModelKey
import com.demo.features.feed.databinding.FeedPageHeaderBinding
import com.demo.foundations.analytics.Analytics
import com.demo.foundations.assemblekit.ViewPage

/**
 * Title + two buttons. Lives at `R.id.feed_header_slot`.
 *
 * Mavericks migration notes:
 *  - Used to emit `FeedEvent.RefreshRequested` / `ToggleBannerRequested`
 *    onto the host bus and let the Activity translate that into repo /
 *    state calls. With a shared `FeedShellViewModel` we can call the
 *    behaviour directly — the buttons become 1-line invocations of VM
 *    methods, no event indirection, no protocol to keep in sync.
 *  - The Page still does not know about the Assembly, the host
 *    activity, or sibling Pages. It only knows about the VM it
 *    `consume`s — exactly what AGENTS.md Rule 2 prescribes.
 */
internal class FeedHeaderPage : ViewPage() {

    private var binding: FeedPageHeaderBinding? = null

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View {
        val b = FeedPageHeaderBinding.inflate(inflater, parent, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View) {
        val b = binding ?: return
        // The shell VM is provided by the Activity at assembly scope.
        // `requireConsume` (over `consume`) makes the missing-provides
        // case a hard fail at install time — much better DX than a
        // null click handler that silently does nothing on tap.
        val viewModel = requireConsume(FeedShellViewModelKey)

        b.btnRefresh.setOnClickListener {
            Analytics.logEvent("feed_header_refresh_click")
            viewModel.refresh()
        }
        b.btnToggleBanner.setOnClickListener {
            Analytics.logEvent("feed_header_toggle_banner_click")
            viewModel.toggleBanner()
        }
    }

    override fun onDestroyView() {
        binding = null
    }
}
