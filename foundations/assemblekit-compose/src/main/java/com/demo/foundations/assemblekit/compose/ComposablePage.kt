package com.demo.foundations.assemblekit.compose

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.demo.foundations.assemblekit.Page
import com.demo.foundations.assemblekit.PageContext

/**
 * The Jetpack Compose flavour of [Page].
 *
 * `ComposablePage` lets you author a Page entirely in Compose without
 * forcing the AssembleKit core (which is XML/View-based) to depend on
 * the Compose toolchain. It lives in the sibling Gradle module
 * `:foundations:assemblekit-compose`, so consumers that never use
 * Compose (a feature module on the View track, the analytics bizlib,
 * etc.) keep their toolchain cost at zero.
 *
 * ## What you implement
 *
 * Subclasses implement a single `@Composable Content()` function — the
 * exact mirror of `ViewPage.onCreateView`. Everything else (lifecycle,
 * SavedState, scopes, buses, `PageContext`, the Mavericks `pageViewModel`
 * delegate) is inherited from [Page] and behaves identically to the
 * View flavour.
 *
 * ```kotlin
 * internal class FeedHeaderPage : ComposablePage() {
 *     // Just like a ViewPage — same delegate from :foundations:assemblekit.
 *     private val vm: FeedHeaderViewModel by pageViewModel()
 *
 *     @Composable
 *     override fun Content() {
 *         // Reach the Shell VM via the Page-scoped CompositionLocal.
 *         // Type-checked, page-scoped, no DI container required.
 *         val shell = composeRequireConsume(FeedShellViewModelKey)
 *         val title by shell.collectAsState(FeedShellState::title)
 *         Text(text = title)
 *     }
 * }
 * ```
 *
 * ## How the bridge works
 *
 * `materialize()` returns a [ComposeView] holding the page's content.
 * Two responsibilities are wired in there:
 *
 *  1. **Disposal**: `ViewCompositionStrategy.DisposeOnLifecycleDestroyed`
 *     hooked to the Page's own lifecycle. When the Page is detached the
 *     composition is torn down deterministically, even though the parent
 *     Activity may live on (e.g. across `Assembly.replace`).
 *  2. **Context injection**: the surrounding `CompositionLocalProvider`
 *     publishes the Page's [PageContext] as [LocalPageContext]. Inside
 *     `Content()` any descendant can call [requirePageContext] /
 *     [composeRequireConsume] without taking the context as a parameter
 *     — analogous to how `view.requirePageContext()` works in the View
 *     world (see [com.demo.foundations.assemblekit.ViewTreePageContext]).
 *
 * ## Anti-patterns
 *
 * - **Do not** call `LocalLifecycleOwner.current` and use it as the
 *   Page's lifecycle owner — that resolves to the Activity. Use the
 *   inherited [lifecycle] property if you need the Page-scoped one.
 * - **Do not** create your own `ComposeView` and ignore `materialize()`.
 *   The framework relies on the returned view being the View it can
 *   stamp with [com.demo.foundations.assemblekit.ViewTreePageContext]
 *   and add to the assembly container.
 * - **Do not** reach for `KoinJavaComponent.get<ShellViewModel>()` or
 *   any other DI lookup inside `Content()` — that is the exact escape
 *   hatch AGENTS.md Rule M6 (and the View-tree PageContext design) was
 *   written to close. Use [composeRequireConsume] instead.
 */
abstract class ComposablePage(
    explicitId: String? = null,
) : Page(explicitId = explicitId) {

    /**
     * The page's UI, expressed in Compose. Called inside a composition
     * that has [LocalPageContext] already provided, so descendants can
     * call [requirePageContext] / [composeRequireConsume].
     */
    @Composable
    protected abstract fun Content()

    final override fun materialize(inflater: LayoutInflater, parent: ViewGroup): View {
        // Capture the PageContext at materialize-time. It is created by
        // the framework just before this call and stays stable for the
        // Page's lifetime, so reading it once here is safe.
        val pageContext: PageContext = context

        return ComposeView(parent.context).apply {
            // Tie composition lifetime to the *Page's* lifecycle, not the
            // host Activity's. When the framework detaches this Page (e.g.
            // via Assembly.replace) the composition disposes immediately
            // even though the Activity is still RESUMED.
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnLifecycleDestroyed(this@ComposablePage),
            )
            setContent {
                CompositionLocalProvider(LocalPageContext provides pageContext) {
                    Content()
                }
            }
        }
    }
}
