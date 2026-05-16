package com.demo.features.home

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.demo.bizlibs.user.UserRepository
import com.demo.features.home.databinding.HomeActivityBinding
import com.demo.foundations.analytics.Analytics
import com.demo.foundations.common.Result
import com.demo.foundations.communicate.IAppEnv
import com.demo.foundations.communicate.ILogoutService
import com.demo.foundations.communicate.IRemoteConfig
import com.demo.foundations.communicate.ServiceRegistry
import com.demo.foundations.communicate.get
import com.demo.foundations.communicate.getOrNull
import com.demo.foundations.router.Router

/**
 * Step 2: after login, show the current user's profile and offer a
 * button that navigates to `:features:profile` via the router.
 *
 * Notice how this Activity has NO compile-time dependency on
 * `:features:profile` — navigation goes through `:foundations:router`,
 * which is what makes "no feature-to-feature dependency" enforceable.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: HomeActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = HomeActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Analytics.logPageView("home")

        // ---- SPI demo #1: IAppEnv (read app-layer info) -----------
        ServiceRegistry.getOrNull<IAppEnv>()?.let { env ->
            Analytics.logEvent(
                "home_app_env",
                mapOf(
                    "versionName" to env.appVersionName,
                    "versionCode" to env.appVersionCode,
                    "debuggable" to env.isDebuggable,
                    "channel" to env.channel,
                ),
            )
        }

        // ---- SPI demo #2: IRemoteConfig (lazy-registered) ---------
        val remoteConfig = ServiceRegistry.get<IRemoteConfig>()
        val showProfileButton = remoteConfig.getBoolean(
            IRemoteConfig.Keys.HOME_SHOW_PROFILE_BUTTON,
            default = true,
        )
        val welcomePrefix = remoteConfig.getString(
            IRemoteConfig.Keys.HOME_WELCOME_PREFIX,
            default = "Hello,",
        )
        binding.goProfile.visibility = if (showProfileButton) View.VISIBLE else View.GONE

        when (val r = UserRepository.loadCurrentUser()) {
            is Result.Success -> {
                binding.userId.text = "userId: ${r.data.userId}"
                binding.nickname.text = "$welcomePrefix ${r.data.nickname}"
                binding.bio.text = r.data.bio
            }
            is Result.Failure -> {
                binding.userId.text = "load failed: ${r.throwable.message}"
            }
        }

        binding.goProfile.setOnClickListener {
            Analytics.logEvent("home_goto_profile_click")
            Analytics.logNavigation(from = "home", to = "profile")
            Router.navigate(this, Router.Paths.PROFILE)
        }

        // ---- SPI demo #3: ILogoutService (action-style) -----------
        binding.logout.setOnClickListener {
            Analytics.logEvent("home_logout_click")
            ServiceRegistry.get<ILogoutService>().logout(this, reason = "user_home")
        }
    }
}
