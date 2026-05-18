package com.demo.foundations.assemblekit

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

/**
 * The classic flavour of [Page]: produce a [View] from XML (or build it
 * by hand) and react via `findViewById` / view bindings.
 *
 * This is the default sub-type the AssembleKit demo apps use today and
 * the path that exists everywhere `:foundations:assemblekit` ships.
 * Pick it whenever you are not already on Compose.
 *
 * Three extension points:
 *
 *  - [onCreateView]   *(required)* — inflate / build the root view.
 *    Use `inflater.inflate(layoutId, parent, /* attachToRoot = */ false)`;
 *    never attach the view yourself, the framework adds it to the
 *    assembly container after applying layout params.
 *  - [onViewCreated]  *(optional)* — wire listeners, bind ViewModels,
 *    subscribe to buses. Do **not** start work here that should only
 *    run while STARTED/RESUMED — observe [lifecycle] instead.
 *  - [onDestroyView]  *(optional)* — release view-bound resources.
 *    Lifecycle / coroutine scope teardown is handled for you.
 *
 * Typical usage:
 * ```kotlin
 * internal class LoginBodyPage : ViewPage() {
 *     private val viewModel: LoginBodyViewModel by pageViewModel()
 *
 *     override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup) =
 *         LoginPageBodyBinding.inflate(inflater, parent, false).root
 *
 *     override fun onViewCreated(view: View) {
 *         viewModel.onEach(LoginBodyState::canSubmit) { /* … */ }
 *     }
 * }
 * ```
 */
abstract class ViewPage(
    explicitId: String? = null,
) : Page(explicitId = explicitId) {

    /** Inflate or build the root [View] of this Page. */
    protected abstract fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View

    /** Called once immediately after [onCreateView] returns. */
    protected open fun onViewCreated(view: View) = Unit

    /** Called once after the view is removed and the Page is being torn down. */
    protected open fun onDestroyView() = Unit

    // ---- glue into base Page ------------------------------------------

    final override fun materialize(inflater: LayoutInflater, parent: ViewGroup): View {
        val view = onCreateView(inflater, parent)
        onViewCreated(view)
        return view
    }

    final override fun onDestroy() {
        onDestroyView()
    }
}
