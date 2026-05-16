package com.demo.features.login

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.demo.bizlibs.account.AccountRepository
import com.demo.features.login.databinding.LoginActivityBinding
import com.demo.foundations.analytics.Analytics
import com.demo.foundations.communicate.IRemoteConfig
import com.demo.foundations.communicate.ServiceRegistry
import com.demo.foundations.communicate.getOrNull
import com.demo.foundations.router.Router
import com.demo.foundations.ui.Toaster

/**
 * Step 1 of the demo flow: enter username/password → tap "Login" →
 * delegate to `:bizlibs:account` → navigate to `home` via `:foundations:router`.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: LoginActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LoginActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Analytics.logPageView("login")

        // SPI demo: let the app/server decide whether to prefill the
        // demo credentials (useful to turn off for screenshots /
        // production builds).
        val prefillDemo = ServiceRegistry.getOrNull<IRemoteConfig>()
            ?.getBoolean(IRemoteConfig.Keys.LOGIN_PREFILL_DEMO_CREDENTIALS, default = true)
            ?: true
        if (prefillDemo) {
            binding.username.setText("demo-user")
            binding.password.setText("demo-pass")
        }

        binding.loginButton.setOnClickListener {
            val u = binding.username.text?.toString().orEmpty()
            val p = binding.password.text?.toString().orEmpty()

            Analytics.logEvent("login_button_click", mapOf("username" to u))

            val result = AccountRepository.login(u, p)
            when (result) {
                is com.demo.foundations.common.Result.Success -> {
                    Toaster.short(this, "Welcome, ${result.data.username}")
                    Analytics.logNavigation(from = "login", to = "home")
                    Router.navigate(this, Router.Paths.HOME)
                    finish()
                }
                is com.demo.foundations.common.Result.Failure -> {
                    Toaster.short(this, "Login failed: ${result.throwable.message}")
                    Analytics.logEvent(
                        "login_failure",
                        mapOf("reason" to (result.throwable.message ?: "unknown")),
                    )
                }
            }
        }
    }
}
