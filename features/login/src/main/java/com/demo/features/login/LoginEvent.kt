package com.demo.features.login

/**
 * The event contract shared by the Pages that make up the Login screen.
 *
 * Why a sealed class instead of free-form `Any` events?
 *  - Each Page declares which `LoginEvent` subtypes it cares about, and
 *    the compiler tells us if a subtype is unhandled — much safer than
 *    string-named events.
 *  - All login traffic stays *inside* the feature module. Other features
 *    do not import these classes; they listen for higher-level
 *    contracts (e.g. session changes) through SPI / Mavericks state.
 *
 * Scope of each event is documented next to it. As a rule:
 *  - `*Changed` / `*Clicked` flow on the **AssemblyBus** (sibling Pages).
 *  - `*Finished` flow on the **HostBus** (host decides routing).
 */
internal sealed class LoginEvent {

    /** Body broadcasts text changes so Bottom can light up the button. */
    data class CredentialsChanged(
        val username: String,
        val password: String,
    ) : LoginEvent()

    /** Bottom asks Body to perform login. */
    data object SubmitClicked : LoginEvent()

    /** Body tells the Host (Activity) what happened, Host decides routing. */
    data class LoginFinished(
        val success: Boolean,
        val username: String?,
        val errorMessage: String?,
    ) : LoginEvent()
}
