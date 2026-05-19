@file:OptIn(com.airbnb.mvrx.InternalMavericksApi::class)

package com.demo.foundations.assemblekit

import androidx.appcompat.app.AppCompatActivity
import com.airbnb.mvrx.ActivityViewModelContext
import com.airbnb.mvrx.InternalMavericksApi
import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.MavericksViewModel
import com.airbnb.mvrx.MavericksViewModelProvider

/**
 * 便捷的 lazy 委托：给 [Page] 提供一个 Mavericks ViewModel，作用域绑在
 * **宿主 Activity 的** ViewModelStore 上（配置变更后会自动恢复），
 * 但 key 是 `pageId::ViewModelClass`，所以每个 Page 拿到的都是自己那份实例。
 *
 * 为什么 VM 存在宿主上而不是 Page 自己上？
 *  - Page 不会跨配置变更存活（框架会通过 `assemble {}` 重建它），但它的 ViewModel
 *    必须要存活下来。
 *  - Mavericks 内置的 `@PersistState` 和 SavedState 集成机制都假设 ViewModelContext
 *    是 `Activity` / `Fragment`——委托给宿主可以让我们一直待在它支持的范围内。
 *
 * 取舍：当 [Assembly] 在运行时被 `replace()` 之后，已经摘下来的 Page 的 ViewModel
 * 仍然会留在宿主的 store 里，直到宿主自己被销毁。对于典型场景（assembly 的生命周期
 * 等于整个 Activity）这没问题。后续迭代可以加一个 `Assembly.clearViewModels()`
 * 用来主动清理。
 *
 * 用法：
 * ```kotlin
 * class LoginBodyPage : ViewPage() {
 *     private val viewModel: LoginBodyViewModel by pageViewModel()
 *     // ...
 * }
 * ```
 */
inline fun <reified VM : MavericksViewModel<S>, reified S : MavericksState> Page.pageViewModel(
    /** 覆盖存储 key。默认是 `{pageId}::{VM 类名}`。 */
    noinline keyFactory: () -> String = { "$pageId::${VM::class.java.name}" },
): Lazy<VM> = lazy(LazyThreadSafetyMode.NONE) {
    val activity = activityOrNull(this)
        ?: error(
            "pageViewModel() requires the host to be an AppCompatActivity " +
                "(got ${pageHostOrNull(this)?.javaClass?.name}). " +
                "If you're hosting Pages inside a Fragment, switch to " +
                "fragmentPageViewModel() — coming in a follow-up iteration.",
        )

    MavericksViewModelProvider.get(
        viewModelClass = VM::class.java,
        stateClass = S::class.java,
        viewModelContext = ActivityViewModelContext(activity = activity, args = null),
        key = keyFactory(),
    )
}

// ---- 内部 helper（放在这里是为了不泄露 `context` 访问器） ----

@PublishedApi
internal fun pageHostOrNull(page: Page): PageHost? = try {
    // 无反射的快速通道：PageContext 在框架内是 package-private，`context` 属性是 `protected`；
    // 我们通过 Page 自己上的一个 friend 风格的 trampoline 暴露 hostOrNull。
    page.hostOrNullInternal()
} catch (t: Throwable) {
    null
}

@PublishedApi
internal fun activityOrNull(page: Page): AppCompatActivity? =
    pageHostOrNull(page) as? AppCompatActivity
