package com.demo.foundations.assemblekit.local

import java.util.concurrent.ConcurrentHashMap

/**
 * 存放在 [ScopedContainer] 里某个值的、带类型的命名 key。
 *
 * 两个 [name] 相同的 key 仍然**不**相等：身份是按实例比对的，这是有意为之。
 * 也就是说，你不会因为另一个不相关的 module 恰好选了同样的 key 名字而冲突——
 * 每个 module 声明自己的 `val`，这个 `val` *本身*就是身份。
 *
 * 永远把 key 声明为顶级 `val`，让它是单例：
 *
 * ```kotlin
 * val FeedRepositoryKey = pageContextKey<FeedRepository>("feed.repository")
 * val ItemActionsKey    = pageContextKey<ItemActions>("feed.itemActions")
 * ```
 *
 * [name] 纯粹用于诊断（toString / logs）。
 */
class PageContextKey<T> internal constructor(val name: String) {
    override fun toString(): String = "PageContextKey($name)"
}

/**
 * key 工厂方法。务必把返回值存到顶级 `val` 里，确保 provide 端和 consume 端
 * 用的是同一个 key 实例。
 */
fun <T> pageContextKey(name: String): PageContextKey<T> = PageContextKey(name)

/**
 * 一个绑定到某个 scope 的 "locals" map，类比 React Context / Compose CompositionLocal。
 * 每个 [ScopedContainer] 持有一张以 [PageContextKey] 为 key 的扁平 map，
 * 再加上一个可选的 [parent] 做 fallback 查找。
 *
 * **查找语义** ([resolve])：沿着 [parent] 链向上回溯，返回第一个命中的值。这样框架
 * 就可以铺出三层——page → assembly → host——而嵌套的消费方（例如 list 里的 item）
 * 会自动看到不论哪一层 provide 了的那个值。
 *
 * **provide 语义** ([set])：只写入*当前*容器，绝不会写到 parent。子层可以遮盖父层的值，
 * 但永远改不了它。配合只读的 resolve，"谁能改什么" 的审计变得非常简单。
 *
 * 线程安全性：底层是 [ConcurrentHashMap]。主线程上的写（`assemble {}` / `onCreate`
 * 的常见情况）加上后台线程的读（RecyclerView 预绑定等）不需要额外同步即可工作。
 */
class ScopedContainer internal constructor(
    private val parent: ScopedContainer? = null,
    private val debugName: String = "anon",
) {
    private val values = ConcurrentHashMap<PageContextKey<*>, Any?>()

    /** 在*当前* scope 内为 [key] provide [value]。不会动 parent。 */
    operator fun <T> set(key: PageContextKey<T>, value: T) {
        // ConcurrentHashMap 不允许 null 值；我们用一个 sentinel 顶替，
        // 这样当 T 可空时调用方可以合法地 `provides(key, null)`。
        values[key] = value ?: NULL_SENTINEL
    }

    /** 只读当前 scope 里 [set] 进去的值（不走 parent 回退）。 */
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: PageContextKey<T>): T? {
        val raw = values[key] ?: return null
        return if (raw === NULL_SENTINEL) null else raw as T
    }

    /**
     * 先查当前 scope，再沿着 parent 链向上回溯。返回第一个非 `null` 命中，
     * 没有任何 scope provide 过则返回 `null`。
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> resolve(key: PageContextKey<T>): T? {
        var node: ScopedContainer? = this
        while (node != null) {
            val raw = node.values[key]
            if (raw != null) return if (raw === NULL_SENTINEL) null else raw as T
            node = node.parent
        }
        return null
    }

    /** 同 [resolve]，但 key 从未被 provide 过时会抛出带诊断信息的异常。 */
    fun <T> require(key: PageContextKey<T>): T = resolve(key)
        ?: error(
            "No $key was provided in any enclosing scope " +
                "(checked from '$debugName' upwards). Make sure your call site " +
                "is inside an 'assemble { provides($key) { … } ; … }' block " +
                "(or set it on hostLocal before 'assemble').",
        )

    /** 当且仅当 [resolve]（包括 parent 链）能拿到值时返回 true。 */
    fun has(key: PageContextKey<*>): Boolean = resolve<Any>(@Suppress("UNCHECKED_CAST") (key as PageContextKey<Any>)) != null

    /**
     * 清空*当前* scope 里的所有条目。parent 不动，所以后续 [resolve] 仍可以
     * 一路冒泡到宿主层 provide 的东西。
     *
     * [Assembly.replace] 会用它，给新组合一个干净的 `provides` 面，
     * 不至于带上一份残留的旧条目。
     */
    internal fun clearLocalEntries() {
        values.clear()
    }

    override fun toString(): String =
        "ScopedContainer($debugName, ${values.size} entries, parent=${parent?.debugName ?: "-"})"

    internal companion object {
        // ConcurrentHashMap 不允许 null 值；用 sentinel 保留 T 可空时
        // 调用 provide(key, null) 的能力。
        private val NULL_SENTINEL: Any = Any()

        internal fun root(debugName: String): ScopedContainer =
            ScopedContainer(parent = null, debugName = debugName)

        internal fun child(parent: ScopedContainer, debugName: String): ScopedContainer =
            ScopedContainer(parent = parent, debugName = debugName)
    }
}
