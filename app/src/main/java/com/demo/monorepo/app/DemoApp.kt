package com.demo.monorepo.app

import android.app.Application
import com.airbnb.mvrx.Mavericks
import com.demo.features.feed.FeedActivity
import com.demo.features.home.HomeActivity
import com.demo.features.login.LoginActivity
import com.demo.features.profile.ProfileActivity
import com.demo.foundations.analytics.Analytics
import com.demo.foundations.communicate.IAppEnv
import com.demo.foundations.communicate.ILogoutService
import com.demo.foundations.communicate.IRemoteConfig
import com.demo.foundations.communicate.ServiceRegistry
import com.demo.foundations.communicate.register
import com.demo.foundations.communicate.registerLazy
import com.demo.foundations.router.Router
import com.demo.thirdparty.logger.Logger

/**
 * `:app` is the single composition root — the only module allowed to
 * import every layer. Everything else navigates through the router so
 * features stay decoupled.
 */
class DemoApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Install a tag-prefixed sink so demo logs stand out.
        Logger.install { level, tag, message ->
            android.util.Log.println(
                when (level) {
                    Logger.Level.VERBOSE -> android.util.Log.VERBOSE
                    Logger.Level.DEBUG -> android.util.Log.DEBUG
                    Logger.Level.INFO -> android.util.Log.INFO
                    Logger.Level.WARN -> android.util.Log.WARN
                    Logger.Level.ERROR -> android.util.Log.ERROR
                },
                "MonorepoDemo/$tag",
                message,
            )
        }

        // Wire up Airbnb Mavericks (the MVI engine driving every Page).
        // Must be called before any MavericksViewModel is constructed.
        Mavericks.initialize(this)

        // Register feature routes once at app start.
        Router.register(Router.Paths.LOGIN, LoginActivity::class.java)
        Router.register(Router.Paths.HOME, HomeActivity::class.java)
        Router.register(Router.Paths.PROFILE, ProfileActivity::class.java)
        Router.register(Router.Paths.FEED, FeedActivity::class.java)

        // SPI: expose app-layer capabilities to lower layers without
        // forcing them to depend on `:app`. Implementations live here
        // (composition root), the interfaces live in
        // `:foundations:communicate`.
        ServiceRegistry.register<IAppEnv>(AppEnvImpl(this))
        ServiceRegistry.register<ILogoutService>(LogoutServiceImpl())
        // Lazy registration: created on first ServiceRegistry.get() —
        // a good fit for services with non-trivial init.
        ServiceRegistry.registerLazy<IRemoteConfig> { RemoteConfigImpl() }

        Analytics.logEvent("app_start")
    }
}
