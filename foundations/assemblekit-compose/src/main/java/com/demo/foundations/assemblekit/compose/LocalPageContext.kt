package com.demo.foundations.assemblekit.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import com.demo.foundations.assemblekit.PageContext
import com.demo.foundations.assemblekit.local.PageContextKey

/**
 * The Compose-side mirror of the View-tree [PageContext] stamp.
 *
 * Set by [ComposablePage] just inside its `ComposeView`'s `setContent`,
 * so any `@Composable` descendant — including reusable design-system
 * widgets — can reach the enclosing Page's environment with a single
 * call. This is the exact same anti-DI move as
 * [com.demo.foundations.assemblekit.requirePageContext] on `View`:
 * page-scoped, type-checked, no DI container.
 *
 * Default value is `null` so that calling `LocalPageContext.current`
 * from a composable that is *not* hosted inside a `ComposablePage` (e.g.
 * a preview / Paparazzi snapshot) doesn't blow up at composition time
 * — the explicit [requirePageContext] / [composeRequireConsume]
 * helpers will throw with a clear message instead.
 */
val LocalPageContext: ProvidableCompositionLocal<PageContext?> =
    compositionLocalOf { null }

/**
 * Fetch the enclosing [PageContext] or throw if missing. Use this when
 * a composable genuinely cannot render without the page environment
 * (almost every page-scoped widget). For previews that can fall back
 * to a static state, read [LocalPageContext] `.current` directly and
 * branch on `null`.
 */
@Composable
@ReadOnlyComposable
fun requirePageContext(): PageContext =
    LocalPageContext.current ?: error(
        "No PageContext provided to this composition. Did you call this composable " +
            "from outside a ComposablePage (e.g. a @Preview without LocalPageContext)? " +
            "Wrap your preview in `CompositionLocalProvider(LocalPageContext provides fakeContext) { … }`.",
    )

/**
 * Compose mirror of `Page.consume(key)` — walks page → assembly → host
 * and returns the first match, or `null` if no scope provides [key].
 *
 * Prefer [composeRequireConsume] for required dependencies so the error
 * message points at the missing `provides` call site instead of failing
 * later with a nullable-deref further down the tree.
 */
@Composable
@ReadOnlyComposable
fun <T> composeConsume(key: PageContextKey<T>): T? =
    requirePageContext().consume(key)

/**
 * Compose mirror of `Page.requireConsume(key)` — same fall-through
 * lookup as [composeConsume], but throws a helpful error if nobody
 * provided the key in the page/assembly/host chain.
 *
 * Use this to grab the screen's Shell ViewModel inside any descendant
 * composable without taking it as a parameter:
 *
 * ```kotlin
 * @Composable
 * fun NoteActionBar(noteId: String) {
 *     val shell = composeRequireConsume(FeedShellViewModelKey)
 *     // shell.like(noteId), shell.collectAsState(...), etc.
 * }
 * ```
 */
@Composable
@ReadOnlyComposable
fun <T> composeRequireConsume(key: PageContextKey<T>): T =
    requirePageContext().requireConsume(key)
