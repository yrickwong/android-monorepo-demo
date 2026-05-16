package com.demo.monorepo.app

import com.demo.foundations.communicate.IRemoteConfig
import com.demo.thirdparty.logger.Logger

/**
 * `:app`-side implementation of [IRemoteConfig].
 *
 * Backed by an in-memory map for the demo. In production replace this
 * class with a real backend (Firebase Remote Config / in-house service
 * / static json on CDN) — **no feature change required**, because the
 * SPI contract stays the same.
 *
 * Registered into `ServiceRegistry` via `registerLazy` so it is built
 * on first use rather than at `Application.onCreate`. This pattern is
 * a good fit for services with non-trivial init (network call, disk
 * read, native lib load) that aren't needed on every cold start.
 */
internal class RemoteConfigImpl : IRemoteConfig {

    private val booleans: Map<String, Boolean> = mapOf(
        IRemoteConfig.Keys.HOME_SHOW_PROFILE_BUTTON to true,
        IRemoteConfig.Keys.LOGIN_PREFILL_DEMO_CREDENTIALS to true,
    )

    private val strings: Map<String, String> = mapOf(
        IRemoteConfig.Keys.HOME_WELCOME_PREFIX to "Welcome back,",
    )

    private val longs: Map<String, Long> = emptyMap()

    init {
        Logger.i(TAG, "RemoteConfigImpl initialized (entries=${booleans.size + strings.size + longs.size})")
    }

    override fun getBoolean(key: String, default: Boolean): Boolean =
        booleans[key] ?: default

    override fun getString(key: String, default: String): String =
        strings[key] ?: default

    override fun getLong(key: String, default: Long): Long =
        longs[key] ?: default

    private companion object {
        const val TAG = "RemoteConfigImpl"
    }
}
