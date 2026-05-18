package com.demo.features.login

import com.airbnb.mvrx.Async
import com.airbnb.mvrx.Loading
import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.Uninitialized
import com.demo.bizlibs.account.Session

/**
 * Immutable Mavericks state for the body Page.
 *
 * Conventions followed:
 *  - Every field has a default so Mavericks can construct the initial
 *    state from `initial = LoginBodyState()`.
 *  - Derived values (like [canSubmit]) live as computed properties on
 *    the state itself — never reach for them from inside a `setState`.
 */
internal data class LoginBodyState(
    val username: String = "",
    val password: String = "",
    val loginAsync: Async<Session> = Uninitialized,
) : MavericksState {

    /** A pure derivation; recomputed on demand, never persisted. */
    val canSubmit: Boolean
        get() = username.isNotBlank() && password.isNotBlank() && loginAsync !is Loading
}
