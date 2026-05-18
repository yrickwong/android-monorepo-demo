package com.demo.foundations.assemblekit

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

/**
 * Stub for the Compose flavour of [Page]. The DSL already supports it:
 *
 * ```kotlin
 * class FeedHeaderPage : ComposablePage() {
 *     override fun Content() {  // @Composable, once :assemblekit-compose ships
 *         Text("Hello", style = MaterialTheme.typography.titleLarge)
 *     }
 * }
 *
 * assemble(host = this) {
 *     +FeedHeaderPage() at R.id.slot_top   // identical to ViewPage at the call site
 *     +ClassicListPage()                    // co-exists with ViewPages
 * }
 * ```
 *
 * **Why a stub today?**
 *
 *  - Bringing Compose into `:foundations:assemblekit` would force every
 *    consumer to take the Compose toolchain (≈ +1 MB code + Compose
 *    compiler plugin) even if they never use it. Not acceptable for a
 *    multi-platform foundation module.
 *  - Compose itself is moving fast, and we want App teams to opt in on
 *    their own schedule. Holding the integration in a sibling artifact
 *    (`:foundations:assemblekit-compose`) lets that artifact track
 *    Compose's release cadence independently.
 *  - Keeping the type *here* (in the base artifact) keeps the DSL
 *    stable: when the Compose module ships, nothing in `assemble {}`
 *    changes, only the [materialize] implementation provided by a
 *    subclass switches.
 *
 * Until that sibling artifact lands, [materialize] throws with an
 * explicit message so accidental use is caught immediately at runtime
 * (and, since the DSL accepts any `Page`, also caught by integration
 * tests rather than at compile time).
 */
abstract class ComposablePage(
    explicitId: String? = null,
) : Page(explicitId = explicitId) {

    /**
     * Will be annotated `@Composable` once `:foundations:assemblekit-compose`
     * ships. Today the method body is never invoked because
     * [materialize] aborts first.
     */
    protected abstract fun Content()

    final override fun materialize(inflater: LayoutInflater, parent: ViewGroup): View {
        error(
            "ComposablePage requires the :foundations:assemblekit-compose artifact " +
                "to provide the ComposeView wiring. It ships as a stub in v2 — use " +
                "ViewPage for now, or open #assemblekit-compose to track adoption.",
        )
    }
}
