package com.demo.foundations.assemblekit

import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes

/**
 * [Assembly] 里的一个槽位：一个 [Page] 加上"它该挂在哪"。
 *
 * 在 `assemble {}` DSL 块里用 `+MyPage()` 隐式创建；通过 `at(R.id.slot_xxx)`
 * infix 来配置：
 *
 * ```kotlin
 * assemble(container = root) {        // 默认容器作为兜底
 *     +HeaderPage() at R.id.slot_top
 *     +BodyPage()                     // 没写 .at → 回落到 root
 *     +BottomPage() at R.id.slot_bottom
 * }
 * ```
 *
 * 这个类刻意做得极小、构造之后可变（仅在 DSL block 内有效，DSL block 本身是单线程的）——
 * 另一种实现是让 `unaryPlus` 对每个 page 都分配一个包装对象、并强制每次 override 都写
 * `.also { }`，可读性更差。
 */
class MountSpec internal constructor(internal val page: Page) {

    @IdRes
    internal var containerIdOverride: Int = View.NO_ID

    /**
     * 解析出这个 Page 应该 attach 到的 [ViewGroup]。
     *
     * 查找顺序：
     *  1. 如果设置了 [containerIdOverride]，向宿主要这个 id。
     *     找不到直接致命（多半是 slot id 拼错了）。
     *  2. 否则回落到 [defaultContainer]——即传给 `assemble(container = ...)` 的那个。
     *  3. 两者都没有就抛，错误信息会指明两种修复方向。
     */
    internal fun resolveContainer(host: PageHost, defaultContainer: ViewGroup?): ViewGroup {
        if (containerIdOverride != View.NO_ID) {
            return host.findContainer(containerIdOverride)
                ?: error(
                    "Page ${page.javaClass.simpleName} requested mount target " +
                        "id=0x${Integer.toHexString(containerIdOverride)} but the host " +
                        "${host.hostId} has no ViewGroup with that id. " +
                        "Check that the slot lives in the host's setContentView() layout.",
                )
        }
        return defaultContainer
            ?: error(
                "Page ${page.javaClass.simpleName} has no container: " +
                    "the assemble() call did not supply a default container and the " +
                    "Page did not use 'at(R.id.…)' to pick one. " +
                    "Use one of:\n" +
                    "    assemble(container = root) { +MyPage() }              // fallback for all\n" +
                    "    assemble                    { +MyPage() at R.id.slot } // explicit per-page",
            )
    }
}
