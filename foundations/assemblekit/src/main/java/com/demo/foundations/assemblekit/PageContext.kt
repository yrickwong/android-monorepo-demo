package com.demo.foundations.assemblekit

import androidx.lifecycle.ViewModelStoreOwner
import com.demo.foundations.assemblekit.bus.ScopedCommandBus
import com.demo.foundations.assemblekit.bus.ScopedEventBus
import com.demo.foundations.assemblekit.local.ScopedContainer
import kotlinx.coroutines.CoroutineScope

/**
 * 框架注入到每个 [Page] 里的"环境对象"。
 *
 * 把这些引用塞到一个值对象里（而不是让 `Page` 去实现十几个接口），可以让 `Page`
 * 子类专注于 UI 逻辑，且在单元测试里非常容易 mock——你可以拿假对象拼出一个
 * [PageContext]，断言你的 Page 发布 / 订阅了什么，全程不用启 Activity。
 *
 * 暴露了三层、由小到大的 scope：
 *
 *  - [pageScope]     → Page 自身销毁时取消
 *  - [assemblyScope] → 所属 [Assembly] 销毁时取消
 *  - [hostScope]     → [PageHost] 销毁时取消
 *
 * 每个 scope 都有自己独立的 [ScopedEventBus] / [ScopedCommandBus]。
 * 满足用例的最小 scope 就够了——决策矩阵见 `docs/assemblekit.md`。
 *
 * **身份规则：**
 *  - [pageId] 在所属 [Assembly] 内唯一。它同时被用作 Mavericks 的 view-id 和
 *    ViewModel 的 key，配置变更后能正确恢复对应状态。
 */
class PageContext internal constructor(
    val host: PageHost,
    val assembly: Assembly,
    val pageId: String,

    val pageScope: CoroutineScope,
    val assemblyScope: CoroutineScope,
    val hostScope: CoroutineScope,

    val pageBus: ScopedEventBus,
    val assemblyBus: ScopedEventBus,
    val hostBus: ScopedEventBus,

    val pageCommands: ScopedCommandBus,
    val assemblyCommands: ScopedCommandBus,
    val hostCommands: ScopedCommandBus,

    /**
     * Scoped "locals" 容器，类比 React Context / Compose CompositionLocal。
     * 每一层都有自己独立的 [ScopedContainer]，链式串成 page → assembly → host：
     * [pageLocal] 查找未命中时会继续往 assembly 找，再往 host 找。
     *
     * "拿框架里 provide 过的任意值" 这种场景请用 [consume]（或快捷的 `Page.consume(key)`）——
     * 99% 的代码都该走这条路。直接读写某一层（`pageLocal[key] = …`）是给一些进阶覆盖
     * 留的口子，比如 "只让这个 item 遮盖一下 assembly provide 的那个值"。
     */
    val pageLocal: ScopedContainer,
    val assemblyLocal: ScopedContainer,
    val hostLocal: ScopedContainer,

    /**
     * 调用 Mavericks 的 `existingViewModel()` / `activityViewModel()`、想在同宿主下的
     * 多个 Page 间共享 state 时，把这个 owner 传进去。它指向的是宿主的 ViewModelStore，
     * 而**不是**每个 Page 各自的。
     */
    val hostViewModelStoreOwner: ViewModelStoreOwner,
) {
    /**
     * 解析 [key]：按 page → assembly → host 的顺序回溯，返回第一个命中的 provider；
     * 任何一层都没 provide 就返回 `null`。
     *
     * 必需依赖优先用 [requireConsume]，这样错误信息会指明缺失的 `provides` 调用点。
     */
    fun <T> consume(key: com.demo.foundations.assemblekit.local.PageContextKey<T>): T? =
        pageLocal.resolve(key)

    /** 同 [consume]，但 key 从未被 provide 过时会抛异常。 */
    fun <T> requireConsume(key: com.demo.foundations.assemblekit.local.PageContextKey<T>): T =
        pageLocal.require(key)
}
