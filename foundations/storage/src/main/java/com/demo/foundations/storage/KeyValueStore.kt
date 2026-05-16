package com.demo.foundations.storage

import com.demo.thirdparty.logger.Logger
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-local key/value store. Replaceable with DataStore/SharedPreferences.
 */
object KeyValueStore {
    private const val TAG = "KeyValueStore"
    private val map = ConcurrentHashMap<String, String>()

    fun put(key: String, value: String) {
        Logger.d(TAG, "put($key, $value)")
        map[key] = value
    }

    fun get(key: String): String? {
        val v = map[key]
        Logger.d(TAG, "get($key) = $v")
        return v
    }

    fun remove(key: String) {
        Logger.d(TAG, "remove($key)")
        map.remove(key)
    }
}
