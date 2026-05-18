package com.demo.foundations.assemblekit

import android.view.ViewGroup
import android.widget.LinearLayout
import com.demo.foundations.assemblekit.local.PageContextKey

/**
 * DSL marker so blocks of `assemble {}` cannot accidentally call each
 * other's receivers — a common foot-gun in deeply nested Kotlin DSLs.
 */
@DslMarker
annotation class AssemblyDsl

/**
 * Builder handed to the `assemble {}` block. Provides the unary-plus
 * operator for the canonical syntax:
 *
 * ```kotlin
 * assemble(container = root) {
 *     +LoginHeaderPage()
 *     +LoginBodyPage()
 *     +LoginBottomPage()
 * }
 * ```
 *
 * Also exposes [page] for cases where unary-plus is awkward (passing a
 * page returned by a factory) and [whenever] for conditional inclusion.
 */
@AssemblyDsl
class AssemblyBuilder internal constructor(internal val assembly: Assembly) {

    /** Canonical: `+MyPage()` appends to the assembly. */
    operator fun <P : Page> P.unaryPlus(): P {
        assembly.add(this)
        return this
    }

    /** Functional variant: `page(myPageFactory.create())`. */
    fun page(page: Page) {
        assembly.add(page)
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
     * assemble(host = this) {
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
 * Entry point: build an [Assembly] inside [container] and immediately
 * install every Page declared in [block].
 *
 * @param container the ViewGroup that will receive Page views. Use a
 *   `LinearLayout` for simple vertical stacks; a `FrameLayout` /
 *   `ConstraintLayout` if Pages position themselves.
 * @param orientation only honoured when [container] is a [LinearLayout].
 */
fun PageHost.assemble(
    container: ViewGroup,
    orientation: Int = LinearLayout.VERTICAL,
    block: AssemblyBuilder.() -> Unit,
): Assembly {
    val assembly = Assembly(host = this, container = container, orientation = orientation)
    AssemblyBuilder(assembly).block()
    assembly.install()
    return assembly
}
