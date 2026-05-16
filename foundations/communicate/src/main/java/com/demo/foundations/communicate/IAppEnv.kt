package com.demo.foundations.communicate

/**
 * Example SPI that lets lower-layer modules read app-wide environment
 * information owned by the `:app` composition root.
 *
 * The **interface lives here** so any feature/bizlib can see it;
 * the **implementation lives in `:app`** and is registered into
 * [ServiceRegistry] at startup. This way a feature can ask
 * "what's the app version?" without ever importing anything from `:app`.
 */
interface IAppEnv {
    /** Human-readable version, e.g. `"1.0.0"`. */
    val appVersionName: String

    /** Numeric version code, e.g. `1`. */
    val appVersionCode: Long

    /** True when the app is built with `debuggable=true`. */
    val isDebuggable: Boolean

    /** Identifier of the running build flavor / channel (e.g. "googleplay"). */
    val channel: String
}
