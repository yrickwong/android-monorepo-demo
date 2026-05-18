package com.demo.features.login.page

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.demo.features.login.LoginBodyState
import com.demo.features.login.LoginBodyViewModel
import com.demo.features.login.LoginEvent
import com.demo.features.login.databinding.LoginPageBodyBinding
import com.demo.foundations.analytics.Analytics
import com.demo.foundations.assemblekit.Page
import com.demo.foundations.assemblekit.pageViewModel
import com.demo.foundations.communicate.IRemoteConfig
import com.demo.foundations.communicate.ServiceRegistry
import com.demo.foundations.communicate.getOrNull

/**
 * The "owns business state" Page. Holds the Mavericks ViewModel that
 * tracks username/password/login-async, plays the role of:
 *
 *  - **Producer** of [LoginEvent.CredentialsChanged] / [LoginEvent.LoginFinished]
 *  - **Consumer** of [LoginEvent.SubmitClicked] (triggered by Bottom)
 *
 * Notice how nothing here knows about routing or the Activity — that
 * concern lives in the host. The Page only exposes facts ("login
 * succeeded with this user") and lets the host decide.
 */
internal class LoginBodyPage : Page() {

    private val viewModel: LoginBodyViewModel by pageViewModel()

    private var binding: LoginPageBodyBinding? = null

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View {
        val b = LoginPageBodyBinding.inflate(inflater, parent, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View) {
        val b = binding ?: return

        // SPI: ask the app whether to prefill demo credentials. This was
        // previously inlined in LoginActivity — moving it next to the
        // widgets that consume it keeps the logic local.
        val prefill = ServiceRegistry.getOrNull<IRemoteConfig>()
            ?.getBoolean(IRemoteConfig.Keys.LOGIN_PREFILL_DEMO_CREDENTIALS, default = true)
            ?: true
        if (prefill) viewModel.prefill("demo-user", "demo-pass")

        // ---- View -> ViewModel ------------------------------------------------
        b.username.doAfterTextChanged { viewModel.updateUsername(it?.toString().orEmpty()) }
        b.password.doAfterTextChanged { viewModel.updatePassword(it?.toString().orEmpty()) }

        // ---- ViewModel -> View (and bus) -------------------------------------
        // 1) Reflect prefill / external updates back into the EditTexts.
        viewModel.onEach(LoginBodyState::username) { value ->
            if (b.username.text?.toString() != value) b.username.setText(value)
        }
        viewModel.onEach(LoginBodyState::password) { value ->
            if (b.password.text?.toString() != value) b.password.setText(value)
        }

        // 2) Broadcast credential changes so Bottom can enable its button.
        viewModel.onEach(LoginBodyState::username, LoginBodyState::password) { u, p ->
            emitToAssembly(LoginEvent.CredentialsChanged(u, p))
        }

        // 3) Map the Async lifecycle onto UI + host-level events.
        viewModel.onAsync(
            LoginBodyState::loginAsync,
            onSuccess = { session ->
                b.errorMessage.isVisible = false
                emitToHost(
                    LoginEvent.LoginFinished(
                        success = true,
                        username = session.username,
                        errorMessage = null,
                    ),
                )
            },
            onFail = { throwable ->
                val msg = throwable.message ?: "unknown error"
                b.errorMessage.text = msg
                b.errorMessage.isVisible = true
                Analytics.logEvent("login_failure", mapOf("reason" to msg))
                emitToHost(
                    LoginEvent.LoginFinished(
                        success = false,
                        username = null,
                        errorMessage = msg,
                    ),
                )
            },
        )

        // ---- Bus -> ViewModel ------------------------------------------------
        // Bottom asks us to submit; we kick off the suspend call via the VM.
        onAssemblyEvent<LoginEvent.SubmitClicked> {
            Analytics.logEvent("login_button_click")
            viewModel.submit()
        }
    }

    override fun onDestroyView() {
        binding = null
    }
}
