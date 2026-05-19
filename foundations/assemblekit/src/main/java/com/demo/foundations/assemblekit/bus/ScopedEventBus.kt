package com.demo.foundations.assemblekit.bus

import com.demo.thirdparty.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

/**
 * 一个按类型分发的事件 bus，*严格*绑定在某个 owner [CoroutineScope] 上。
 *
 * 为什么我们自己造（而不是用 EventBus / RxBus / 一个全局 flow）：
 *
 *  - **没有全局状态。** 一个 bus 隶属于唯一一个 scope（page / assembly /
 *    host）。这个 scope 被取消时，挂在它上面的所有 collector 会自动停掉——
 *    所以不会因为"永久监听"而意外把销毁了的 View 给吊住。
 *  - **不粘性（not sticky）。** `replay = 0` 意味着新订阅者收不到旧事件。
 *    如果调用方想要*最新值*语义，请改用 Mavericks 的 state——事件是事件，状态是状态。
 *  - **emit 非挂起。** [emit] 用的是 `tryEmit`，生产者永远不会被阻塞。
 *    buffer 故意设了上限（64），失控的生产者会以"丢事件 + 打 warning"的形式
 *    暴露出来，而不会把内存吃爆。
 *
 * 调用点借助 reified [on] 是类型安全的，但通道里实际传输的是 `Any`——这样多个
 * 不相干的事件类型可以共用一个 channel。
 *
 * @param tag debug tag，会出现在 log 里。请传点有辨识度的，比如
 *            `"PageBus(login_body)"` 或 `"AssemblyBus(login)"`。
 */
class ScopedEventBus(private val tag: String) {

    /**
     * `extraBufferCapacity = 64` 可以让突发事件（比如每个按键都触发一次的
     * 文本变化）在不给生产者施加背压的情况下被缓冲下来。同时在飞超过 64 个，
     * 基本可以判定是 bug。
     */
    val flow = MutableSharedFlow<Any>(
        replay = 0,
        extraBufferCapacity = BUFFER_SIZE,
    )

    /** Fire-and-forget 发射。返回事件是否被成功缓冲。 */
    fun emit(event: Any): Boolean {
        val accepted = flow.tryEmit(event)
        if (!accepted) {
            Logger.w(LOG_TAG, "[$tag] dropped event: ${event.javaClass.simpleName}")
        }
        return accepted
    }

    /**
     * 在 [collectScope] 范围内订阅类型为 [E] 的事件。[collectScope] 被取消时
     * 订阅会自动取消。
     *
     * 返回的 [Job] 很少用到——除非你需要在 scope 结束之前提前取消订阅
     * （比如一次性的监听器）。
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
