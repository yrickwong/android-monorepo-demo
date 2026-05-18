package com.demo.features.feed.page

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.demo.features.feed.databinding.FeedPageBannerBinding
import com.demo.foundations.assemblekit.ViewPage

/**
 * Pure decoration Page added/removed by `Assembly.replace { }` to show
 * that re-composition is host-driven and lossless: the rest of the
 * Pages (header / list / footer) keep their state and their host-bus
 * subscriptions across the swap.
 */
internal class FeedBannerPage : ViewPage() {

    private var binding: FeedPageBannerBinding? = null

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View {
        val b = FeedPageBannerBinding.inflate(inflater, parent, false)
        binding = b
        return b.root
    }

    override fun onDestroyView() {
        binding = null
    }
}
