package com.demo.foundations.assemblekit

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

/**
 * [Page] 的经典子类型：从 XML inflate（或手写 View 组装）拿到一个 [View]，
 * 再通过 `findViewById` / view binding 操作。
 *
 * 这是 AssembleKit 示例工程当前默认使用的子类型，也是 `:foundations:assemblekit`
 * 任何下游都必然能用上的路径。只要你不是已经在用 Compose，就选它。
 *
 * 三个扩展点：
 *
 *  - [onCreateView]   *(必填)* —— inflate / 组装根 View。
 *    用 `inflater.inflate(layoutId, parent, /* attachToRoot = */ false)`；
 *    不要自己把 View attach 上去，框架会在应用完布局参数后把它加到 assembly 容器里。
 *  - [onViewCreated]  *(可选)* —— 接 listener、绑 ViewModel、订 bus。
 *    **不要**在这里启动只能在 STARTED/RESUMED 时跑的工作——请改为观察 [lifecycle]。
 *  - [onDestroyView]  *(可选)* —— 释放绑定到 View 上的资源。
 *    生命周期 / 协程作用域的拆解由框架代劳。
 *
 * 典型用法：
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

    /** Inflate 或组装本 Page 的根 [View]。 */
    protected abstract fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View

    /** 在 [onCreateView] 返回后立即调用一次。 */
    protected open fun onViewCreated(view: View) = Unit

    /** 在 view 被移除、Page 正在拆解时调用一次。 */
    protected open fun onDestroyView() = Unit

    // ---- 与基类 Page 的衔接 -------------------------------------------

    final override fun materialize(inflater: LayoutInflater, parent: ViewGroup): View {
        val view = onCreateView(inflater, parent)
        onViewCreated(view)
        return view
    }

    final override fun onDestroy() {
        onDestroyView()
    }
}
