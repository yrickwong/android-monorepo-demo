package com.demo.foundations.router

import android.content.Context
import android.content.Intent
import com.demo.thirdparty.logger.Logger
import java.util.concurrent.ConcurrentHashMap

/**
 * Schema-based navigation registry. Features register themselves at app
 * start by mapping a path (e.g. `home`) to an Activity class. Other
 * modules navigate without holding a hard reference to the target.
 *
 * NOTE: feature-to-feature navigation is intentionally routed through
 * this layer so that `:features:*` never depend on each other directly
 * — which is exactly the rule enforced by `checkDependencyRules`.
 */
object Router {

    private const val TAG = "Router"
    private val routes = ConcurrentHashMap<String, Class<*>>()

    fun register(path: String, target: Class<*>) {
        Logger.d(TAG, "register($path → ${target.simpleName})")
        routes[path] = target
    }

    fun navigate(context: Context, path: String, extras: Map<String, String> = emptyMap()) {
        val target = routes[path]
            ?: error("[Router] no route registered for path = $path")
        Logger.i(TAG, "navigate → $path (${target.simpleName})")
        val intent = Intent(context, target).apply {
            extras.forEach { (k, v) -> putExtra(k, v) }
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
    }

    object Paths {
        const val LOGIN = "login"
        const val HOME = "home"
        const val PROFILE = "profile"
    }
}
