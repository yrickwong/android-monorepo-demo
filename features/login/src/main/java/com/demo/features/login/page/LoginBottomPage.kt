package com.demo.features.login.page

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.demo.features.login.LoginEvent
import com.demo.features.login.databinding.LoginPageBottomBinding
import com.demo.foundations.assemblekit.ViewPage

/**
 * The "all I do is glow on click" Page. Demonstrates a Page that owns
 * **no state** and **no ViewModel** — it just translates UI events
 * into bus events and bus events back into UI.
 *
 * The button stays disabled until [LoginEvent.CredentialsChanged]
 * arrives from [LoginBodyPage] with non-blank values. On click we
 * publish [LoginEvent.SubmitClicked] back onto the assembly bus so
 * Body can perform the actual login.
 *
 * Note that Bottom does *not* listen to `LoginBodyState.loginAsync`
 * directly — that would couple it to Body's internals. Instead, Body
 * republishes the relevant signals onto the bus. This keeps Pages
 * swappable: a future "biometric login" Page can plug in next to
 * Bottom without either of them touching the other.
 */
internal class LoginBottomPage : ViewPage() {

    private var binding: LoginPageBottomBinding? = null

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View {
        val b = LoginPageBottomBinding.inflate(inflater, parent, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View) {
        val b = binding ?: return

        b.loginButton.setOnClickListener {
            // Briefly show the spinner here too; Body will hide it again
            // once the Async terminates (Success / Fail). If we ever
            // delete Body, this still degrades gracefully.
            b.loadingIndicator.isVisible = true
            b.loginButton.isEnabled = false
            emitToAssembly(LoginEvent.SubmitClicked)
        }

        onAssemblyEvent<LoginEvent.CredentialsChanged> { event ->
            val ready = event.username.isNotBlank() && event.password.isNotBlank()
            // Only flip enabled when we're *not* mid-flight; otherwise we'd
            // override the "disabled-while-loading" set above on every text
            // change made after the user tapped Login (unlikely but cheap to guard).
            if (!b.loadingIndicator.isVisible) {
                b.loginButton.isEnabled = ready
            }
        }

        // Body publishes LoginFinished to the *host* bus (so the Activity
        // can route), but Bottom also needs to know to reset the spinner.
        // Listening on the host bus from a Page is allowed; it's just a
        // wider scope. We document that with the explicit `onHostEvent`
        // call rather than mixing scopes silently.
        onHostEvent<LoginEvent.LoginFinished> {
            b.loadingIndicator.isVisible = false
            // Re-enable the button so the user can retry on failure;
            // on success the host typically navigates away before this
            // matters.
            b.loginButton.isEnabled = true
        }
    }

    override fun onDestroyView() {
        binding = null
    }
}
