package com.demo.bizlibs.user

import com.demo.bizlibs.account.AccountRepository
import com.demo.foundations.analytics.Analytics
import com.demo.foundations.common.Result
import com.demo.foundations.common.runCatchingResult
import com.demo.foundations.network.HttpClient
import com.demo.thirdparty.logger.Logger

data class UserProfile(
    val userId: String,
    val nickname: String,
    val bio: String,
)

/**
 * Reads the current user's profile. Depends on `:bizlibs:account` to
 * resolve the signed-in user id, which is a perfectly legal
 * bizlib-to-bizlib dependency.
 */
object UserRepository {

    private const val TAG = "UserRepository"

    fun loadCurrentUser(): Result<UserProfile> = runCatchingResult {
        val session = AccountRepository.currentSession()
            ?: error("not logged in")

        Logger.d(TAG, "loadCurrentUser(userId=${session.userId})")
        Analytics.logEvent("user_profile_load", mapOf("userId" to session.userId))
        HttpClient.get("https://demo.local/api/user/${session.userId}").getOrNull()

        UserProfile(
            userId = session.userId,
            nickname = "Demo-${session.userId.takeLast(4)}",
            bio = "Hello from the monorepo demo!",
        )
    }
}
