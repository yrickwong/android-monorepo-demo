package com.demo.foundations.assemblekit.bus

import com.demo.thirdparty.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

/**
 * A type-routed event bus that is *strictly* bound to an owner [CoroutineScope].
 *
 * Why we built our own (instead of EventBus / RxBus / a global flow):
 *
 *  - **No global state.** A bus belongs to a single scope (page / assembly /
 *    host). When that scope is cancelled, every collector launched on it
 *    stops automatically — so we cannot accidentally leak a destroyed View
 *    by listening "forever".
 *  - **Not sticky.** `replay = 0` means a new subscriber does not receive
 *    old events. If callers need *latest value* semantics they should use
 *    a Mavericks state instead — events are events, state is state.
 *  - **Non-suspending emit.** [emit] uses `tryEmit` so producers never
 *    block. The buffer is intentionally bounded (64) so a runaway producer
 *    surfaces as dropped events with a warning rather than OOM.
 *
 * The bus is type-safe at the call site via reified [on], but the wire
 * format is `Any` so multiple unrelated event types can share one channel.
 *
 * @param tag debug tag, used in log lines. Pass something descriptive like
 *            `"PageBus(login_body)"` or `"AssemblyBus(login)"`.
 */
class ScopedEventBus(private val tag: String) {

    /**
     * `extraBufferCapacity = 64` lets bursts of events (e.g. text changes on
     * every keystroke) buffer without back-pressuring the producer. Anything
     * over 64 in flight is almost certainly a bug.
     */
    val flow = MutableSharedFlow<Any>(
        replay = 0,
        extraBufferCapacity = BUFFER_SIZE,
    )

    /** Fire-and-forget emission. Returns whether the event was buffered. */
    fun emit(event: Any): Boolean {
        val accepted = flow.tryEmit(event)
        if (!accepted) {
            Logger.w(LOG_TAG, "[$tag] dropped event: ${event.javaClass.simpleName}")
        }
        return accepted
    }

    /**
     * Subscribe to events of type [E] within [collectScope]. The subscription
     * is automatically cancelled when [collectScope] is cancelled.
     *
     * The returned [Job] is rarely needed — only use it if you need to
     * unsubscribe before the scope ends (e.g. one-shot listeners).
     */
    inline fun <reified E : Any> on(
        collectScope: CoroutineScope,
        noinline block: suspend (E) -> Unit,
    ): Job = collectScope.launch {
        flow.filterIsInstance<E>().collect { block(it) }
    }

    companion object {
        private const val LOG_TAG = "ScopedEventBus"
        private const val BUFFER_SIZE = 64
    }
}
