package com.demo.features.login

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.demo.features.login.databinding.LoginActivityBinding
import com.demo.features.login.page.LoginBodyPage
import com.demo.features.login.page.LoginBottomPage
import com.demo.features.login.page.LoginHeaderPage
import com.demo.foundations.analytics.Analytics
import com.demo.foundations.assemblekit.PageHostActivity
import com.demo.foundations.assemblekit.assemble
import com.demo.foundations.router.Router
import com.demo.foundations.ui.Toaster

/**
 * The Activity is now intentionally tiny: its only job is to inflate a
 * container, declare which Pages live on this screen, and listen on the
 * host bus for the one event it actually cares about — *whether the
 * login finished*.
 *
 * Everything else (text input, validation, submit-on-click, async state,
 * analytics) is owned by the three Pages, which can be reordered,
 * gated behind RemoteConfig, or replaced individually without touching
 * this file. Compare with `git log -p LoginActivity.kt` to see how much
 * code moved out.
 */
class LoginActivity : PageHostActivity() {

    private lateinit var binding: LoginActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LoginActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Analytics.logPageView("login")

        // ---- Declare the screen as a sequence of Pages -------------------
        // This is the only place where "what does Login look like?" is
        // expressed. Add/remove/reorder Pages here.
        assemble(container = binding.assemblyContainer) {
            +LoginHeaderPage()
            +LoginBodyPage()
            +LoginBottomPage()
        }

        // ---- Host-level concerns: routing, toasts, top-level analytics ---
        // The Pages emit a single LoginFinished event onto the host bus;
        // the Activity is the only thing that knows how to navigate.
        hostBus.on<LoginEvent.LoginFinished>(lifecycleScope) { event ->
            if (event.success) {
                Toaster.short(this@LoginActivity, "Welcome, ${event.username}")
                Analytics.logNavigation(from = "login", to = "home")
                Router.navigate(this@LoginActivity, Router.Paths.HOME)
                finish()
            } else {
                Toaster.short(
                    this@LoginActivity,
                    "Login failed: ${event.errorMessage ?: "unknown"}",
                )
            }
        }
    }
}
