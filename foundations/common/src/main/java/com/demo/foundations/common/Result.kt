package com.demo.foundations.common

/**
 * Lightweight result wrapper used across the demo.
 *
 * Lives in `:foundations:common` so it can be referenced by every layer
 * (foundations, bizlibs, features, app) without creating layering issues.
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Failure(val throwable: Throwable) : Result<Nothing>()

    fun getOrNull(): T? = (this as? Success)?.data
    fun isSuccess(): Boolean = this is Success
}

inline fun <T> runCatchingResult(block: () -> T): Result<T> = try {
    Result.Success(block())
} catch (t: Throwable) {
    Result.Failure(t)
}
