package com.demo.foundations.assemblekit.bus

import com.demo.thirdparty.logger.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Request-response counterpart to [ScopedEventBus].
 *
 * Use this when one Page needs to *ask* another for a value (or wait for a
 * confirmation) rather than just broadcasting an event. Example: the header
 * Page asks the body Page to validate its form and return a boolean result.
 *
 * Semantics:
 *  - Multiple responders for the same [Cmd] type are allowed but only the
 *    first one to answer wins (the others' answers are dropped). In
 *    practice one type → one responder is the recommended pattern.
 *  - If no responder is registered when [request] is called, the request
 *    hangs until either a responder appears or the timeout fires.
 *  - All deliveries happen on the responder's [CoroutineScope]; when that
 *    scope cancels the subscription is torn down.
 *
 * @param tag debug tag, e.g. `"AssemblyCmd(login)"`.
 */
class ScopedCommandBus(private val tag: String) {

    /**
     * `replay = 0` keeps semantics aligned with [ScopedEventBus] — late
     * subscribers do not see past requests. Buffer of 64 protects against
     * accidental floods.
     */
    private val flow = MutableSharedFlow<Envelope>(
        replay = 0,
        extraBufferCapacity = BUFFER_SIZE,
    )

    /**
     * Register a responder for command type [Cmd]. The responder runs on
     * [collectScope] and is unregistered when [collectScope] is cancelled.
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
     * Send [cmd] and suspend until a registered responder returns. Returns
     * `null` if [timeoutMillis] elapses with no answer.
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
