package com.demo.foundations.assemblekit

import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.savedstate.SavedStateRegistryOwner
import com.demo.foundations.assemblekit.bus.ScopedCommandBus
import com.demo.foundations.assemblekit.bus.ScopedEventBus
import com.demo.foundations.assemblekit.local.ScopedContainer

/**
 * [Assembly] 的承载者——通常是一个 `Activity`、`Fragment` 或 `Dialog`。
 * 宿主拥有其下所有 Page 都能看到的*最外层* scope：
 *
 *  - [hostBus]      —— 范围限定在该宿主内的广播事件
 *  - [hostCommands] —— 范围限定在该宿主内的 request/response 通道
 *  - lifecycle / ViewModelStore / SavedStateRegistry —— 供 Page 在宿主之下
 *    构建各自的 [PageScope]
 *
 * 宿主 scope **严格大于** Assembly scope（一个 Activity 可以同时容纳多个
 * Assembly——比如主内容区 + 底部抽屉——它们共用同一个 host bus）。
 *
 * 实现类是 [PageHostActivity] / [PageHostFragment]；
 * 如果你要自定义宿主（比如基于 Compose 的），直接实现这个接口即可。
 */
interface PageHost : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    /** 用于诊断 / Mavericks view id 的稳定标识。 */
    val hostId: String

    /** 宿主范围的广播 bus，生命周期与宿主一致。 */
    val hostBus: ScopedEventBus

    /** 宿主范围的 request/response bus，生命周期与宿主一致。 */
    val hostCommands: ScopedCommandBus

    /**
     * 宿主范围的 "locals" 容器。任何放进来的东西都可以被该宿主下的每一个 Page 通过
     * [PageContext.consume] / `Page.consume(key)` 拿到。
     *
     * 典型用法：把宿主已经持有的长生命周期依赖（`AppEnv`、登录态 session、路由）
     * 塞进来，子 Page 就不用再通过构造参数传递。
     *
     * ```kotlin
     * class FeedActivity : PageHostActivity() {
     *     override fun onCreate(s: Bundle?) {
     *         super.onCreate(s)
     *         hostLocal[FeedRepositoryKey] = FeedRepository.real()
     *         assemble { +HeaderPage(); +FeedListPage() }
     *     }
     * }
     * ```
     */
    val hostLocal: ScopedContainer

    /**
     * 在宿主的视图树里按 id 找一个 [ViewGroup]。`at(R.id.…)` DSL 用它把单个 Page
     * 钉到宿主 `setContentView()` 布局的指定槽位上。
     *
     * 找不到 id 就返回 `null`（也可能是宿主还没调用 `setContentView` 就提前调了
     * `assemble {}`——这种情况请修调用点，不要让这个方法默默吞掉）。
     */
    fun findContainer(@IdRes id: Int): ViewGroup?
}

/**
 * Activity 宿主的便捷基类。配合 `assemble {}` 使用：
 *
 * ```kotlin
 * class LoginActivity : PageHostActivity() {
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         val root = FrameLayout(this).also { setContentView(it) }
 *         assemble(container = root) {
 *             +LoginHeaderPage()
 *             +LoginBodyPage()
 *             +LoginBottomPage()
 *         }
 *     }
 * }
 * ```
 *
 * 等 Mavericks 在 app 级别接入后，`MavericksAppCompatActivity` 会是一个很自然的替代；
 * 目前为了让依赖树尽量小，我们只依赖 `AppCompatActivity`。子类可以自由换父类——
 * 真正有意义的是 [PageHost] 这个接口。
 */
abstract class PageHostActivity : AppCompatActivity(), PageHost {

    override val hostId: String by lazy { "act-${javaClass.simpleName}-${hashCode()}" }

    override val hostBus: ScopedEventBus by lazy {
        ScopedEventBus(tag = "HostBus($hostId)")
    }

    override val hostCommands: ScopedCommandBus by lazy {
        ScopedCommandBus(tag = "HostCmd($hostId)")
    }

    override val hostLocal: ScopedContainer by lazy {
        ScopedContainer.root(debugName = "hostLocal($hostId)")
    }

    override fun findContainer(@IdRes id: Int): ViewGroup? = findViewById(id)

    // Activity 自身已经实现了 ViewModelStoreOwner / LifecycleOwner /
    // SavedStateRegistryOwner，这里不用再接什么了。
}

/**
 * Fragment 宿主的便捷基类。行为与 [PageHostActivity] 一致；
 * 适合把页面装配放在 fragment 里（例如 ViewPager2 的某个 tab）。
 */
abstract class PageHostFragment : Fragment, PageHost {

    constructor() : super()
    constructor(contentLayoutId: Int) : super(contentLayoutId)

    override val hostId: String by lazy { "frg-${javaClass.simpleName}-${hashCode()}" }

    override val hostBus: ScopedEventBus by lazy {
        ScopedEventBus(tag = "HostBus($hostId)")
    }

    override val hostCommands: ScopedCommandBus by lazy {
        ScopedCommandBus(tag = "HostCmd($hostId)")
    }

    override val hostLocal: ScopedContainer by lazy {
        ScopedContainer.root(debugName = "hostLocal($hostId)")
    }

    override fun findContainer(@IdRes id: Int): ViewGroup? = view?.findViewById(id)

    /** 与 Activity 的 `lifecycleScope` 写法对齐的便捷访问器。 */
    @Suppress("unused")
    protected val hostScope get() = lifecycleScope
}
