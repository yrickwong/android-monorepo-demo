package com.demo.foundations.communicate

import android.content.Context

/**
 * **Action-style SPI.**
 *
 * Orchestrates a global "logout" — typically: clear session, clear
 * caches, navigate back to the login screen, optionally toast a hint.
 *
 * The orchestration logic lives in `:app` (composition root) because
 * it needs to coordinate multiple modules (`:bizlibs:account`,
 * `:foundations:router`, etc.). Features can trigger it without
 * importing any of those — they just hold an [ILogoutService].
 */
interface ILogoutService {
    /**
     * Performs a full logout. The implementation is responsible for
     * clearing user state and navigating the user away from any
     * screen that requires an active session.
     *
     * @param context any non-null Context (Activity preferred for
     *                cleaner navigation animations).
     * @param reason  free-form tag used for analytics
     *                ("user", "session_expired", "force_kick", …).
     */
    fun logout(context: Context, reason: String = "user")
}
