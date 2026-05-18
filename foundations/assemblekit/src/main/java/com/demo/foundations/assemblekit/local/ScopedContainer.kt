package com.demo.foundations.assemblekit.local

import java.util.concurrent.ConcurrentHashMap

/**
 * Typed, named key for a value stored in a [ScopedContainer].
 *
 * Two keys with the same [name] are still **not** equal: identity is by
 * instance, on purpose. This means you cannot accidentally clash with
 * an unrelated module that happens to pick the same key name — each
 * module declares its own `val` and that `val` *is* the identity.
 *
 * Always declare keys as top-level `val`s so they're singletons:
 *
 * ```kotlin
 * val FeedRepositoryKey = pageContextKey<FeedRepository>("feed.repository")
 * val ItemActionsKey    = pageContextKey<ItemActions>("feed.itemActions")
 * ```
 *
 * The [name] is purely for diagnostics (toString / logs).
 */
class PageContextKey<T> internal constructor(val name: String) {
    override fun toString(): String = "PageContextKey($name)"
}

/**
 * Factory for keys. Always store the result in a top-level `val` so the
 * same key instance is used at provide-site and consume-site.
 */
fun <T> pageContextKey(name: String): PageContextKey<T> = PageContextKey(name)

/**
 * A scope-bounded "locals" map, à la React Context / Compose
 * CompositionLocal. Each [ScopedContainer] holds a flat map of values
 * keyed by [PageContextKey], plus an optional [parent] for fallback
 * lookup.
 *
 * **Lookup semantics** ([resolve]): walk up the [parent] chain and
 * return the first match. This lets the framework lay out three layers
 * — page → assembly → host — and have nested consumers (e.g. items
 * inside a list) automatically see whichever layer provided the value.
 *
 * **Provide semantics** ([set]): writes only to *this* container, never
 * to the parent. A child can shadow a parent's value but never mutate
 * it. Combined with read-only resolution, this keeps "who can change
 * what" trivially auditable.
 *
 * Thread-safety: backed by [ConcurrentHashMap]. Writes from the main
 * thread (the common case in `assemble {}` / `onCreate`) plus reads
 * from background threads (RecyclerView pre-binding etc.) work without
 * extra synchronization.
 */
class ScopedContainer internal constructor(
    private val parent: ScopedContainer? = null,
    private val debugName: String = "anon",
) {
    private val values = ConcurrentHashMap<PageContextKey<*>, Any?>()

    /** Provide [value] for [key] in *this* scope. Does not touch the parent. */
    operator fun <T> set(key: PageContextKey<T>, value: T) {
        // ConcurrentHashMap forbids null values; we use a sentinel so callers
        // can `provides(key, null)` legitimately if T is nullable.
        values[key] = value ?: NULL_SENTINEL
    }

    /** Read the value [set] in this scope only (no parent fallback). */
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: PageContextKey<T>): T? {
        val raw = values[key] ?: return null
        return if (raw === NULL_SENTINEL) null else raw as T
    }

    /**
     * Read the value for [key] from this scope first, then walk up the
     * parent chain. Returns the first non-`null` match, or `null` if no
     * scope provides this key.
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

    /** Like [resolve] but throws a helpful error if the key was never provided. */
    fun <T> require(key: PageContextKey<T>): T = resolve(key)
        ?: error(
            "No $key was provided in any enclosing scope " +
                "(checked from '$debugName' upwards). Make sure your call site " +
                "is inside an 'assemble { provides($key) { … } ; … }' block " +
                "(or set it on hostLocal before 'assemble').",
        )

    /** True iff [resolve] would find a value (in this scope or any parent). */
    fun has(key: PageContextKey<*>): Boolean = resolve<Any>(@Suppress("UNCHECKED_CAST") (key as PageContextKey<Any>)) != null

    override fun toString(): String =
        "ScopedContainer($debugName, ${values.size} entries, parent=${parent?.debugName ?: "-"})"

    internal companion object {
        // ConcurrentHashMap doesn't allow null values; sentinel preserves the
        // ability to provide(key, null) when T is nullable.
        private val NULL_SENTINEL: Any = Any()

        internal fun root(debugName: String): ScopedContainer =
            ScopedContainer(parent = null, debugName = debugName)

        internal fun child(parent: ScopedContainer, debugName: String): ScopedContainer =
            ScopedContainer(parent = parent, debugName = debugName)
    }
}
