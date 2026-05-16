package com.demo.foundations.communicate

/**
 * **Read-style SPI.**
 *
 * Lets any feature/bizlib query runtime configuration owned by `:app`
 * (or by whoever wires the implementation in). In production this is
 * typically backed by Firebase Remote Config, Apollo, a CDN-served json,
 * or an in-house config service.
 *
 * Demo implementation (`RemoteConfigImpl` in `:app`) is just an
 * in-memory map, but the interface is shaped exactly like a real one,
 * so swapping in a real backend later means **changing one class in
 * `:app`** — no feature touches required.
 */
interface IRemoteConfig {

    fun getBoolean(key: String, default: Boolean = false): Boolean

    fun getString(key: String, default: String = ""): String

    fun getLong(key: String, default: Long = 0L): Long

    /** Well-known keys used by demo features. Real apps usually keep
     *  these in a separate `*Keys` object close to the consumer. */
    object Keys {
        const val HOME_SHOW_PROFILE_BUTTON = "home.show_profile_button"
        const val HOME_WELCOME_PREFIX = "home.welcome_prefix"
        const val LOGIN_PREFILL_DEMO_CREDENTIALS = "login.prefill_demo_credentials"
    }
}
