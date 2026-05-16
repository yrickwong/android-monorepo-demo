package com.demo.monorepo.app

import android.app.Activity
import android.content.Context
import com.demo.bizlibs.account.AccountRepository
import com.demo.foundations.analytics.Analytics
import com.demo.foundations.communicate.ILogoutService
import com.demo.foundations.router.Router
import com.demo.foundations.ui.Toaster
import com.demo.thirdparty.logger.Logger

/**
 * `:app`-side implementation of [ILogoutService].
 *
 * Orchestrates a full sign-out across multiple modules. Lives here
 * because only `:app` is allowed to depend on everything it needs:
 *   - `:bizlibs:account` — to clear the session
 *   - `:foundations:router` — to navigate back to login
 *   - `:foundations:ui` — to surface a toast
 *
 * Features call into it via the SPI and never see any of the above.
 */
internal class LogoutServiceImpl : ILogoutService {

    override fun logout(context: Context, reason: String) {
        val userId = AccountRepository.logout()
        Logger.i(TAG, "logout(reason=$reason, userId=$userId)")
        Analytics.logEvent(
            "logout_triggered",
            mapOf("reason" to reason, "userId" to userId),
        )

        Toaster.short(context, "Logged out")

        // Send the user back to login and clear the back stack so
        // pressing Back from login won't bring them to a stale screen.
        Router.navigate(context, Router.Paths.LOGIN)
        if (context is Activity) {
            context.finishAffinity()
        } else {
            // If the caller passes an Application context we can't
            // tear down the back stack from here. Router already adds
            // FLAG_ACTIVITY_NEW_TASK for us in that case.
            Logger.w(TAG, "logout invoked with non-Activity context; back stack not cleared")
        }
    }

    private companion object {
        const val TAG = "LogoutServiceImpl"
    }
}
