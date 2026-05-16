package com.demo.foundations.network

import com.demo.foundations.common.Result
import com.demo.foundations.common.runCatchingResult
import com.demo.thirdparty.logger.Logger

/**
 * Tiny fake HTTP client. In a real codebase this would wrap OkHttp/Retrofit.
 */
object HttpClient {
    private const val TAG = "HttpClient"

    /** Simulates a network call returning a [Result] of the response body. */
    fun get(url: String): Result<String> = runCatchingResult {
        Logger.d(TAG, "GET $url")
        // Pretend we hit the network and got 200 OK with a body.
        "fake-response-for:$url"
    }
}
