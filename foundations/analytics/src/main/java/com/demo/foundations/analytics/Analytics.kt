package com.demo.foundations.analytics

import com.demo.thirdparty.logger.Logger

/**
 * Unified analytics entry-point. Every business event (login click,
 * page view, navigation...) is funneled through here in the demo so
 * that `:foundations:analytics` is the single source of truth for
 * tracking, and any switch of backend (e.g. Firebase, internal SDK)
 * is a one-file change.
 */
object Analytics {

    private const val TAG = "Analytics"

    fun logEvent(name: String, params: Map<String, Any?> = emptyMap()) {
        val rendered = if (params.isEmpty()) {
            name
        } else {
            name + " " + params.entries.joinToString(prefix = "{", postfix = "}") {
                "${it.key}=${it.value}"
            }
        }
        Logger.i(TAG, "event → $rendered")
    }

    fun logPageView(page: String, extras: Map<String, Any?> = emptyMap()) {
        logEvent("page_view", buildMap {
            put("page", page)
            putAll(extras)
        })
    }

    fun logNavigation(from: String, to: String) {
        logEvent("navigation", mapOf("from" to from, "to" to to))
    }
}
