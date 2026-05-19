package com.demo.foundations.assemblekit

import android.view.View

/**
 * "View-tree PageContext" helpers — the missing piece for deep custom
 * views that want to reach the assembly's shared state (typically the
 * Shell ViewModel) without parameter-drilling.
 *
 * Mental model: identical to AndroidX' `ViewTreeLifecycleOwner` /
 * `ViewTreeViewModelStoreOwner`, and morally identical to Compose's
 * `LocalComposition`. The framework stamps the active [PageContext]
 * onto the Page's root View; any descendant View can walk up the
 * `parent` chain to find the nearest stamped context.
 *
 * **Who stamps the tag?** (You almost never call [setPageContext] by hand.)
 *  - [Page.performAttach] stamps the View returned by `materialize` —
 *    every Page's root view, regardless of flavour ([ViewPage],
 *    [AsyncViewPage], or `ComposablePage` from
 *    `:foundations:assemblekit-compose`), is eligible immediately
 *    after the framework adds it to the container.
 *  - [com.demo.foundations.assemblekit.list.ListPage]'s internal adapter
 *    stamps each row's `itemView` so deep subviews / nested RecyclerView
 *    ViewHolders inside a row can also resolve the parent Page's context.
 *
 * **Who reads it?** Any custom View that needs to call into the Shell
 * ViewModel. Typical usage from a reusable widget:
 *
 * ```kotlin
 * class NoteActionBar(ctx: Context, attrs: AttributeSet?) : LinearLayout(ctx, attrs) {
 *     private var noteId: String? = null
 *     fun bind(noteId: String) { this.noteId = noteId }
 *
 *     init {
 *         likeButton.setOnClickListener {
 *             requirePageContext()
 *                 .requireConsume(FeedShellViewModelKey)
 *                 .likeOne(noteId ?: return@setOnClickListener)
 *         }
 *     }
 * }
 * ```
 *
 * The widget exposes a one-field setter (`bind(noteId)`) — every other
 * dependency (the ViewModel, the analytics tracker, theme tokens) flows
 * in via the context lookup. The widget is reusable in *any* AssembleKit
 * page that provides the same key; it does not take a feature-specific
 * type as a constructor param.
 *
 * **Why not use a DI framework instead?** A DI lookup
 * (`KoinJavaComponent.get<FeedShellViewModel>()`) would also work
 * mechanically, but it silently bypasses the page-scoping invariant —
 * the view gets *any* live instance, not the one bound to *this* host.
 * See `docs/mvi-rules.md` § "Why not Koin/Hilt".
 *
 * **Lifecycle / safety:** the tag is a hard reference to the
 * [PageContext], which itself holds the host. The framework clears the
 * stamped reference on detach (see [com.demo.foundations.assemblekit.Page.performDetach]
 * and [com.demo.foundations.assemblekit.list.ListPage.onDestroyView]),
 * so the View tree cannot keep the host alive after teardown.
 */
fun View.setPageContext(context: PageContext?) {
    setTag(R.id.assemblekit_page_context_tag, context)
}

/**
 * Walk up the View parent chain looking for the nearest stamped
 * [PageContext]. Returns `null` if no ancestor has one — most often
 * that means the View was instantiated by `LayoutInflater` outside any
 * Page (e.g. a preview, a dialog), in which case the caller should
 * decide how to degrade.
 */
fun View.findPageContext(): PageContext? {
    var current: View? = this
    while (current != null) {
        val tagged = current.getTag(R.id.assemblekit_page_context_tag)
        if (tagged is PageContext) return tagged
        current = current.parent as? View
    }
    return null
}

/**
 * Like [findPageContext] but throws if no PageContext is reachable
 * from this View. Use this in widgets that legitimately *require*
 * being mounted inside an AssembleKit page — the error message makes
 * the "this widget was used outside its supported context" bug
 * obvious instead of letting it surface as a null-deref several
 * frames later.
 */
fun View.requirePageContext(): PageContext =
    findPageContext()
        ?: error(
            "No PageContext is reachable from this View. " +
                "View=${this.javaClass.simpleName}. " +
                "Make sure this View is mounted inside a Page (or a ListPage row); " +
                "directly inflated views (previews, raw dialogs) need to call " +
                "setPageContext(...) on the root before lookup works.",
        )
