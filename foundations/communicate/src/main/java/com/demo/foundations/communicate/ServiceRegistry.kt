package com.demo.foundations.communicate

import com.demo.thirdparty.logger.Logger
import java.util.concurrent.ConcurrentHashMap

/**
 * Cross-layer Service Provider Interface (SPI) registry.
 *
 * Purpose: let lower-layer modules (typically `:features:*` / `:bizlibs:*`)
 * obtain capabilities provided by higher-layer modules (typically `:app`)
 * **without** creating a hard module-to-module dependency that would
 * violate the layering rules enforced by `checkDependencyRules`.
 *
 * ## How to use
 *
 * 1. Declare a service interface in this module (or in any module visible
 *    to both producer and consumer):
 *
 *    ```kotlin
 *    interface IAppEnv {
 *        val appVersionName: String
 *        val isDebuggable: Boolean
 *    }
 *    ```
 *
 * 2. Register an implementation **once** during app startup (usually in
 *    `Application.onCreate`):
 *
 *    ```kotlin
 *    ServiceRegistry.register<IAppEnv>(AppEnvImpl(this))
 *    ```
 *
 *    Use [registerLazy] when the implementation is expensive to build:
 *    ```kotlin
 *    ServiceRegistry.registerLazy<IAppEnv> { AppEnvImpl(this) }
 *    ```
 *
 * 3. Consume from any feature/bizlib that depends on
 *    `:foundations:communicate`:
 *
 *    ```kotlin
 *    val env = ServiceRegistry.get<IAppEnv>()
 *    Logger.i("Home", "appVersion=${env.appVersionName}")
 *    ```
 *
 * ## Why not just use a static singleton?
 *
 * Because the producer of the implementation lives in a *higher* layer
 * than the consumer. A direct singleton would force `feature -> app`,
 * which is forbidden. SPI flips the dependency: the **interface** lives
 * in a layer both sides can see, and the **implementation** is wired in
 * at runtime by the composition root (`:app`).
 */
object ServiceRegistry {

    private const val TAG = "ServiceRegistry"

    private val singletons = ConcurrentHashMap<Class<*>, Any>()
    private val providers = ConcurrentHashMap<Class<*>, () -> Any>()

    /** Register an eagerly-constructed implementation. */
    fun <T : Any> register(api: Class<T>, impl: T) {
        Logger.d(TAG, "register(${api.simpleName})")
        singletons[api] = impl
    }

    /**
     * Register a lazy provider. The provider is invoked at most once on
     * the first [get]/[getOrNull] call and the resulting instance is
     * cached. Useful when the implementation is expensive to construct
     * or has start-up side effects.
     */
    fun <T : Any> registerLazy(api: Class<T>, provider: () -> T) {
        Logger.d(TAG, "registerLazy(${api.simpleName})")
        @Suppress("UNCHECKED_CAST")
        providers[api] = provider as () -> Any
    }

    /** Retrieve an implementation or throw if none is registered. */
    fun <T : Any> get(api: Class<T>): T = getOrNull(api)
        ?: error("[ServiceRegistry] no implementation registered for ${api.name}")

    /** Retrieve an implementation or `null` if none is registered. */
    fun <T : Any> getOrNull(api: Class<T>): T? {
        singletons[api]?.let { return api.cast(it) }
        val provider = providers[api] ?: return null
        val instance = provider.invoke()
        singletons[api] = instance
        providers.remove(api)
        return api.cast(instance)
    }

    /** Visible for testing — wipe every registered service. */
    fun reset() {
        singletons.clear()
        providers.clear()
    }
}

// --------------------------------------------------------------------
// Reified inline helpers — preferred call-site API
// --------------------------------------------------------------------

inline fun <reified T : Any> ServiceRegistry.register(impl: T): Unit =
    register(T::class.java, impl)

inline fun <reified T : Any> ServiceRegistry.registerLazy(noinline provider: () -> T): Unit =
    registerLazy(T::class.java, provider)

inline fun <reified T : Any> ServiceRegistry.get(): T = get(T::class.java)

inline fun <reified T : Any> ServiceRegistry.getOrNull(): T? = getOrNull(T::class.java)
