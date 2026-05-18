package com.demo.foundations.assemblekit

import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.IdRes
import com.demo.foundations.assemblekit.local.PageContextKey

/**
 * DSL marker so blocks of `assemble {}` cannot accidentally call each
 * other's receivers — a common foot-gun in deeply nested Kotlin DSLs.
 */
@DslMarker
annotation class AssemblyDsl

/**
 * Builder handed to the `assemble {}` block. Provides the unary-plus
 * operator for the canonical syntax, with optional `at(R.id.…)` for
 * pinning individual Pages to specific layout slots:
 *
 * ```kotlin
 * assemble(container = root) {                 // default container
 *     +LoginHeaderPage()                       // → root
 *     +LoginBodyPage() at R.id.body_slot       // → R.id.body_slot
 *     +LoginBottomPage()                       // → root
 * }
 * ```
 *
 * Also exposes [page] for cases where unary-plus is awkward (passing a
 * page returned by a factory) and [whenever] for conditional inclusion.
 */
@AssemblyDsl
class AssemblyBuilder internal constructor(internal val assembly: Assembly) {

    /**
     * Canonical: `+MyPage()` appends to the assembly. Returns a
     * [MountSpec] so the call site can chain `at(R.id.…)`.
     */
    operator fun <P : Page> P.unaryPlus(): MountSpec {
        val spec = MountSpec(this)
        assembly.add(spec)
        return spec
    }

    /**
     * Pin this page to a specific slot in the host layout, by view id.
     * The id must resolve to a [ViewGroup] inside the host's
     * `setContentView()` tree at the time `assemble {}` runs.
     *
     * ```kotlin
     * +HeaderPage() at R.id.slot_top
     * ```
     */
    infix fun MountSpec.at(@IdRes containerId: Int): MountSpec = apply {
        containerIdOverride = containerId
    }

    /** Functional variant: `page(myPageFactory.create())`. */
    fun page(page: Page): MountSpec {
        val spec = MountSpec(page)
        assembly.add(spec)
        return spec
    }

    /**
     * Conditional inclusion. Useful for AB tests / RemoteConfig gating:
     *
     * ```kotlin
     * whenever(showBanner) { +PromoBannerPage() }
     * ```
     */
    inline fun whenever(cond: Boolean, block: AssemblyBuilder.() -> Unit) {
        if (cond) block()
    }

    /**
     * Provide a value at **assembly scope**. Visible to every Page in
     * this Assembly via `consume(key)`, and to anything those Pages
     * hand their [PageContext] to (e.g. items inside a `ListPage`).
     *
     * Call this *before* the Pages that depend on it — values are read
     * eagerly during attach.
     *
     * ```kotlin
     * val FeedRepositoryKey = pageContextKey<FeedRepository>("feed.repo")
     *
     * assemble {
     *     provides(FeedRepositoryKey, FeedRepository.real())
     *     +FeedHeaderPage()
     *     +FeedListPage()
     * }
     * ```
     */
    fun <T> provides(key: PageContextKey<T>, value: T) {
        assembly.assemblyLocal[key] = value
    }
}

/**
 * Entry point: build an [Assembly] inside this host and immediately
 * install every Page declared in [block].
 *
 * Two valid usages:
 *
 *  - **Single container** (legacy / simple screens):
 *    ```kotlin
 *    assemble(container = root) {
 *        +HeaderPage()
 *        +BodyPage()
 *        +BottomPage()
 *    }
 *    ```
 *
 *  - **Multi-slot layout** (Pages pin themselves):
 *    ```kotlin
 *    assemble {
 *        +HeaderPage()  at R.id.slot_top
 *        +BodyPage()    at R.id.slot_middle
 *        +BottomPage()  at R.id.slot_bottom
 *    }
 *    ```
 *
 *  - **Mixed**: provide a default container *and* let individual Pages
 *    override with `at(…)` when needed.
 *
 * @param container Optional default container for Pages that do not
 *   pin themselves with `at(R.id.…)`. Pass `null` (or omit) for a
 *   pure multi-slot layout — but then every Page MUST use `at(...)`,
 *   otherwise install throws.
 * @param orientation Honoured only when the resolved mount target is
 *   a [LinearLayout]; ignored otherwise.
 */
fun PageHost.assemble(
    container: ViewGroup? = null,
    orientation: Int = LinearLayout.VERTICAL,
    block: AssemblyBuilder.() -> Unit,
): Assembly {
    val assembly = Assembly(host = this, container = container, orientation = orientation)
    AssemblyBuilder(assembly).block()
    assembly.install()
    return assembly
}
