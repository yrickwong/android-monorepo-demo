package com.demo.foundations.assemblekit

import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.IdRes
import com.demo.foundations.assemblekit.local.PageContextKey

/**
 * DSL 标记：让 `assemble {}` 内嵌的各层 block 互相不能"误调用"对方的 receiver——
 * 这是 Kotlin 深嵌 DSL 里常见的坑。
 */
@DslMarker
annotation class AssemblyDsl

/**
 * 传给 `assemble {}` block 的 builder。提供 unary-plus 操作符作为标准语法，
 * 并配套可选的 `at(R.id.…)` 把单个 Page 钉到具体布局槽位上：
 *
 * ```kotlin
 * assemble(container = root) {                 // 默认容器
 *     +LoginHeaderPage()                       // → root
 *     +LoginBodyPage() at R.id.body_slot       // → R.id.body_slot
 *     +LoginBottomPage()                       // → root
 * }
 * ```
 *
 * 同时暴露 [page]（应对 unary-plus 不便的场景，比如塞一个 factory 生产出来的 page）
 * 和 [whenever]（条件性纳入）。
 */
@AssemblyDsl
class AssemblyBuilder internal constructor(internal val assembly: Assembly) {

    /**
     * 标准用法：`+MyPage()` 追加到 assembly。返回一个 [MountSpec]，
     * 调用点可以继续 `at(R.id.…)` 链式钉位。
     */
    operator fun <P : Page> P.unaryPlus(): MountSpec {
        val spec = MountSpec(this)
        assembly.add(spec)
        return spec
    }

    /**
     * 按 view id 把这个 page 钉到宿主布局里的特定槽位。
     * `assemble {}` 运行时，这个 id 必须能在宿主 `setContentView()` 树里
     * 解析到一个 [ViewGroup]。
     *
     * ```kotlin
     * +HeaderPage() at R.id.slot_top
     * ```
     */
    infix fun MountSpec.at(@IdRes containerId: Int): MountSpec = apply {
        containerIdOverride = containerId
    }

    /** 函数式变体：`page(myPageFactory.create())`。 */
    fun page(page: Page): MountSpec {
        val spec = MountSpec(page)
        assembly.add(spec)
        return spec
    }

    /**
     * 条件性纳入。常用于 AB 实验 / RemoteConfig 开关：
     *
     * ```kotlin
     * whenever(showBanner) { +PromoBannerPage() }
     * ```
     */
    inline fun whenever(cond: Boolean, block: AssemblyBuilder.() -> Unit) {
        if (cond) block()
    }

    /**
     * 在 **assembly 作用域** provide 一个值。本 Assembly 里每个 Page 都能通过
     * `consume(key)` 看到，那些 Page 把自己的 [PageContext] 传给的下游（比如
     * `ListPage` 里的 item）也能看到。
     *
     * 必须在依赖它的 Page 之前调用——值是在 attach 阶段被立即读取的。
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
 * 入口函数：在当前宿主里构建一个 [Assembly]，并立即把 [block] 里声明的 Page
 * 全部安装上去。
 *
 * 两种合法用法：
 *
 *  - **单容器**（老式 / 简单屏幕）：
 *    ```kotlin
 *    assemble(container = root) {
 *        +HeaderPage()
 *        +BodyPage()
 *        +BottomPage()
 *    }
 *    ```
 *
 *  - **多槽位布局**（Page 自己钉位）：
 *    ```kotlin
 *    assemble {
 *        +HeaderPage()  at R.id.slot_top
 *        +BodyPage()    at R.id.slot_middle
 *        +BottomPage()  at R.id.slot_bottom
 *    }
 *    ```
 *
 *  - **混合用**：给一个默认容器，*同时*让需要的 Page 用 `at(…)` 覆盖到具体槽位。
 *
 * @param container 默认容器，给那些不用 `at(R.id.…)` 钉位的 Page 用（可选）。
 *   传 `null`（或省略）就是纯多槽位布局——但这时**每个** Page 都必须用 `at(...)`，
 *   否则 install 会抛。
 * @param orientation 只有当最终解析到的挂载目标是 [LinearLayout] 时才生效，
 *   其它容器忽略。
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
