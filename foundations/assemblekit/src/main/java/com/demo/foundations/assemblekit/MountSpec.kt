package com.demo.foundations.assemblekit

import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes

/**
 * One slot in an [Assembly]: a [Page] plus where it should mount.
 *
 * Created implicitly by `+MyPage()` inside the `assemble {}` DSL block;
 * configured via the `at(R.id.slot_xxx)` infix:
 *
 * ```kotlin
 * assemble(container = root) {        // default container as fallback
 *     +HeaderPage() at R.id.slot_top
 *     +BodyPage()                     // no .at → falls back to root
 *     +BottomPage() at R.id.slot_bottom
 * }
 * ```
 *
 * The class is intentionally tiny and mutable-after-construction (only
 * within the DSL block, which is single-threaded) — the alternative is
 * making `unaryPlus` allocate a wrapping object on every page and forcing
 * a `.also { }` for each override, which reads worse.
 */
class MountSpec internal constructor(internal val page: Page) {

    @IdRes
    internal var containerIdOverride: Int = View.NO_ID

    /**
     * Resolve the [ViewGroup] this Page should be attached to.
     *
     * Lookup order:
     *  1. If [containerIdOverride] is set, ask the host for that id.
     *     Failure is fatal (likely a typo in the slot id).
     *  2. Otherwise, fall back to [defaultContainer] — the one passed
     *     to `assemble(container = ...)`.
     *  3. If neither is available, throw with a hint that points the
     *     caller at both options.
     */
    internal fun resolveContainer(host: PageHost, defaultContainer: ViewGroup?): ViewGroup {
        if (containerIdOverride != View.NO_ID) {
            return host.findContainer(containerIdOverride)
                ?: error(
                    "Page ${page.javaClass.simpleName} requested mount target " +
                        "id=0x${Integer.toHexString(containerIdOverride)} but the host " +
                        "${host.hostId} has no ViewGroup with that id. " +
                        "Check that the slot lives in the host's setContentView() layout.",
                )
        }
        return defaultContainer
            ?: error(
                "Page ${page.javaClass.simpleName} has no container: " +
                    "the assemble() call did not supply a default container and the " +
                    "Page did not use 'at(R.id.…)' to pick one. " +
                    "Use one of:\n" +
                    "    assemble(container = root) { +MyPage() }              // fallback for all\n" +
                    "    assemble(host = this)      { +MyPage() at R.id.slot } // explicit per-page",
            )
    }
}
