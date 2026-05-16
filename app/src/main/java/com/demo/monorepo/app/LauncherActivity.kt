package com.demo.monorepo.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.demo.bizlibs.account.AccountRepository
import com.demo.foundations.analytics.Analytics
import com.demo.foundations.router.Router

/**
 * Decides whether to send the user to the login screen or directly to
 * home — kept tiny on purpose because the launcher is the only public
 * `<intent-filter>` exported by the demo.
 */
class LauncherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Analytics.logPageView("launcher")

        val destination = if (AccountRepository.isLoggedIn()) {
            Router.Paths.HOME
        } else {
            Router.Paths.LOGIN
        }
        Analytics.logNavigation(from = "launcher", to = destination)
        Router.navigate(this, destination)
        finish()
    }
}
