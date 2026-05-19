package com.demo.foundations.assemblekit.bus

import com.demo.thirdparty.logger.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [ScopedEventBus] 的 request-response 对应物。
 *
 * 当一个 Page 需要*向*另一个 Page 询问一个值（或者等待一个确认结果），
 * 而不是只想广播一条事件时，请用这个。例子：header Page 向 body Page 询问
 * 表单是否校验通过，并拿一个 boolean 结果回来。
 *
 * 语义：
 *  - 同一种 [Cmd] 类型允许有多个 responder，但只有第一个返回的胜出
 *    （其他人的结果会被丢弃）。实际推荐"一种类型 → 一个 responder"。
 *  - 调 [request] 时如果没有任何 responder 注册，请求会一直挂着，
 *    直到出现 responder 或者触发超时。
 *  - 所有投递都在 responder 自己的 [CoroutineScope] 上执行；scope 被取消时
 *    订阅会被拆除。
 *
 * @param tag debug tag，比如 `"AssemblyCmd(login)"`。
 */
class ScopedCommandBus(private val tag: String) {

    /**
     * `replay = 0` 保持与 [ScopedEventBus] 一致的语义——晚到的订阅者看不到
     * 之前的请求。buffer = 64 防意外洪泛。
     */
    private val flow = MutableSharedFlow<Envelope>(
        replay = 0,
        extraBufferCapacity = BUFFER_SIZE,
    )

    /**
     * 给命令类型 [Cmd] 注册一个 responder。responder 跑在 [collectScope] 上，
     * [collectScope] 被取消时自动反注册。
     */
    inline fun <reified Cmd : Any, Resp : Any?> respond(
        collectScope: CoroutineScope,
        noinline handler: suspend (Cmd) -> Resp,
    ): Job = collectScope.launch {
        flowInternal().collect { env ->
            if (env.cmd::class.java == Cmd::class.java && !env.deferred.isCompleted) {
                @Suppress("UNCHECKED_CAST")
                try {
                    val result = handler(env.cmd as Cmd)
                    env.deferred.complete(result)
                } catch (t: Throwable) {
                    env.deferred.completeExceptionally(t)
                }
            }
        }
    }

    /**
     * 发送 [cmd] 并挂起，直到某个已注册的 responder 返回结果。
     * 超过 [timeoutMillis] 仍然无人应答则返回 `null`。
     */
    suspend fun <Resp> request(cmd: Any, timeoutMillis: Long = DEFAULT_TIMEOUT_MS): Resp? {
        val deferred = CompletableDeferred<Any?>()
        val delivered = flow.tryEmit(Envelope(cmd, deferred))
        if (!delivered) {
            Logger.w(LOG_TAG, "[$tag] dropped command: ${cmd.javaClass.simpleName}")
            return null
        }
        @Suppress("UNCHECKED_CAST")
        return withTimeoutOrNull(timeoutMillis) { deferred.await() } as Resp?
    }

    @PublishedApi internal fun flowInternal(): MutableSharedFlow<Envelope> = flow

    @PublishedApi internal data class Envelope(
        val cmd: Any,
        val deferred: CompletableDeferred<Any?>,
    )

    companion object {
        private const val LOG_TAG = "ScopedCommandBus"
        private const val BUFFER_SIZE = 64
        private const val DEFAULT_TIMEOUT_MS = 5_000L
    }
}
