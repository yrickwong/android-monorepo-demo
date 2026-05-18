package com.demo.features.feed.page

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.demo.features.feed.FeedEvent
import com.demo.features.feed.databinding.FeedPageHeaderBinding
import com.demo.foundations.analytics.Analytics
import com.demo.foundations.assemblekit.ViewPage

/**
 * Title + two buttons. Lives at `R.id.feed_header_slot`.
 *
 * Pages **do not** know about routing, ViewModels owned by other Pages,
 * or the parent Assembly's structure. The two buttons here just emit
 * events onto the host bus; the Activity is the only thing allowed to
 * mutate the assembly (per the v2 "single-assembly, host-only structural
 * changes" rule).
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
        b.btnRefresh.setOnClickListener {
            Analytics.logEvent("feed_header_refresh_click")
            emitToHost(FeedEvent.RefreshRequested)
        }
        b.btnToggleBanner.setOnClickListener {
            Analytics.logEvent("feed_header_toggle_banner_click")
            emitToHost(FeedEvent.ToggleBannerRequested)
        }
    }

    override fun onDestroyView() {
        binding = null
    }
}
