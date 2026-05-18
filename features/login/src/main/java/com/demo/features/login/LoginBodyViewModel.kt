package com.demo.features.login

import com.airbnb.mvrx.MavericksViewModel
import com.demo.bizlibs.account.AccountRepository
import com.demo.foundations.common.Result

/**
 * MVI ViewModel powering [LoginBodyPage].
 *
 * Notes:
 *  - All mutations go through [setState] so each transition is a single
 *    pure copy of [LoginBodyState]. Avoid touching mutable fields here.
 *  - The actual login call is wrapped in `suspend { ... }.execute { ... }`
 *    which Mavericks turns into the `Async` lifecycle (Uninitialized →
 *    Loading → Success/Fail). The Page subscribes to that Async and
 *    decides what to do on success/failure.
 *  - [AccountRepository.login] returns a `Result<Session>` (not an
 *    exception-throwing API); we re-throw on failure inside the suspend
 *    block so Mavericks can map it onto `Async.Fail(throwable)`.
 */
internal class LoginBodyViewModel(
    initialState: LoginBodyState,
) : MavericksViewModel<LoginBodyState>(initialState) {

    fun updateUsername(value: String) = setState { copy(username = value) }
    fun updatePassword(value: String) = setState { copy(password = value) }

    fun prefill(username: String, password: String) = setState {
        // Only prefill if the user hasn't typed anything yet.
        if (this.username.isBlank() && this.password.isBlank()) {
            copy(username = username, password = password)
        } else {
            this
        }
    }

    fun submit() = withState { current ->
        suspend {
            when (val r = AccountRepository.login(current.username, current.password)) {
                is Result.Success -> r.data
                is Result.Failure -> throw r.throwable
            }
        }.execute { copy(loginAsync = it) }
    }
}
