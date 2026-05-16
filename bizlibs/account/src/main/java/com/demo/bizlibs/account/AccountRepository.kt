package com.demo.bizlibs.account

import com.demo.foundations.analytics.Analytics
import com.demo.foundations.common.Result
import com.demo.foundations.common.runCatchingResult
import com.demo.foundations.network.HttpClient
import com.demo.foundations.storage.KeyValueStore
import com.demo.thirdparty.logger.Logger

/**
 * Owns account / session state. Used by `:features:login` to perform
 * the (fake) sign-in flow and by `:features:home` / `:bizlibs:user`
 * to check whether the user is signed in.
 */
object AccountRepository {

    private const val TAG = "AccountRepository"
    private const val KEY_TOKEN = "account.token"
    private const val KEY_USER_ID = "account.userId"

    fun login(username: String, password: String): Result<Session> = runCatchingResult {
        Logger.i(TAG, "login(username=$username)")
        Analytics.logEvent("account_login_attempt", mapOf("username" to username))

        // Fake remote call; any non-empty password "succeeds".
        require(username.isNotBlank() && password.isNotBlank()) {
            "username/password must not be blank"
        }
        HttpClient.get("https://demo.local/api/login").getOrNull()

        val session = Session(
            userId = "u-${username.hashCode().toUInt()}",
            token = "tk-${System.currentTimeMillis()}",
            username = username,
        )
        KeyValueStore.put(KEY_TOKEN, session.token)
        KeyValueStore.put(KEY_USER_ID, session.userId)
        Analytics.logEvent("account_login_success", mapOf("userId" to session.userId))
        session
    }

    fun currentSession(): Session? {
        val token = KeyValueStore.get(KEY_TOKEN) ?: return null
        val userId = KeyValueStore.get(KEY_USER_ID) ?: return null
        return Session(userId = userId, token = token, username = "")
    }

    fun isLoggedIn(): Boolean = currentSession() != null

    /**
     * Clears the stored session. Returns the previously-active userId
     * (or null) so callers can log analytics about who was kicked.
     */
    fun logout(): String? {
        val userId = KeyValueStore.get(KEY_USER_ID)
        Logger.i(TAG, "logout(userId=$userId)")
        KeyValueStore.remove(KEY_TOKEN)
        KeyValueStore.remove(KEY_USER_ID)
        Analytics.logEvent("account_logout", mapOf("userId" to userId))
        return userId
    }
}

data class Session(
    val userId: String,
    val token: String,
    val username: String,
)
