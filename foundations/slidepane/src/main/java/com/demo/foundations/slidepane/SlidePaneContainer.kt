package com.demo.foundations.slidepane

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.MainThread
import androidx.core.view.ViewCompat
import androidx.customview.widget.ViewDragHelper
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import com.demo.foundations.slidepane.gesture.ScrollDirectionListener
import com.demo.foundations.slidepane.gesture.VerticalGestureObserver
import com.demo.foundations.slidepane.gesture.internal.VerticalGestureBroadcaster
import com.demo.foundations.slidepane.internal.PaneFragmentManagerHelper
import com.demo.foundations.slidepane.lifecycle.PaneLifecycleObserver
import com.demo.foundations.slidepane.spi.PaneProvider
import com.demo.foundations.slidepane.spi.PaneRegistry
import com.demo.foundations.slidepane.spi.PaneWidthSpec
import com.demo.foundations.slidepane.strategy.EdgeGestureStrategy
import com.demo.foundations.slidepane.strategy.NestedScrollPolicy
import com.demo.foundations.slidepane.strategy.PaneLoadingStrategy
import com.demo.foundations.slidepane.strategy.PaneTransitionAnimator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 三屏式滑动主框架容器。
 *
 * ## 业务接入
 * ```kotlin
 * // 1. 注册业务 Pane（通常在 Application 启动时）
 * PaneRegistry.Default.register(HomePaneProvider())
 * PaneRegistry.Default.register(ProfilePaneProvider())
 * PaneRegistry.Default.register(MessagesPaneProvider())
 *
 * // 2. 在 Activity 中 bind
 * slidePaneContainer.bind(supportFragmentManager, lifecycle)
 * ```
 *
 * 框架完全不感知业务，业务通过 [PaneProvider] SPI 接入。
 */
@PaneApi
class SlidePaneContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // ==================== 配置 / 策略 ====================
    private var config: SlidePaneConfig = SlidePaneConfig.Default
    private var transitionAnimator: PaneTransitionAnimator? = null
    private var nestedScrollPolicy: NestedScrollPolicy? = null
    private var edgeGestureStrategy: EdgeGestureStrategy? = null
    private var loadingStrategy: PaneLoadingStrategy = PaneLoadingStrategy.Eager

    // ==================== Fragment 管理 ====================
    private var fragmentHelper: PaneFragmentManagerHelper? = null
    private var registry: PaneRegistry = PaneRegistry.Default

    // ==================== 三个槽位容器 ====================
    private val centerContainer: FrameLayout = FrameLayout(context).apply {
        id = ViewCompat.generateViewId()
    }
    private val startContainer: FrameLayout = FrameLayout(context).apply {
        id = ViewCompat.generateViewId()
        visibility = INVISIBLE
    }
    private val endContainer: FrameLayout = FrameLayout(context).apply {
        id = ViewCompat.generateViewId()
        visibility = INVISIBLE
    }

    // ==================== 状态 ====================
    private var currentState: PaneState = PaneState.CENTER
    private var slideEnabled: Boolean = true
    private val slotEnabled = mutableMapOf(
        PaneSlot.START to true,
        PaneSlot.END to true
    )

    // ==================== 手势辅助 ====================
    private val dragHelper: ViewDragHelper
    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private val verticalBroadcaster = VerticalGestureBroadcaster(context)

    private var downX = 0f
    private var downY = 0f
    private var horizontalIntercepted = false
    private var verticalLockedToChild = false

    // ==================== 观察者 ====================
    private val paneLifecycleObservers = mutableListOf<PaneLifecycleObserver>()
    private var lastNotifiedActiveSlot: PaneSlot = PaneSlot.CENTER
    private var lastNotifiedFraction: Float = 0f

    init {
        // 构造时即按 z-index 添加：start/end 在底，center 在顶
        addView(startContainer, makeFullParams())
        addView(endContainer, makeFullParams())
        addView(centerContainer, makeFullParams())

        dragHelper = ViewDragHelper.create(this, 1f, DragCallback())
    }

    private fun makeFullParams() = LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )

    // ==================== 公开 API ====================

    /** 绑定 FragmentManager / Lifecycle / 注册中心 / 配置 */
    @MainThread
    @PaneApi
    fun bind(
        fragmentManager: FragmentManager,
        lifecycle: Lifecycle,
        registry: PaneRegistry = PaneRegistry.Default,
        config: SlidePaneConfig = SlidePaneConfig.Default
    ) {
        this.registry = registry
        this.config = config
        this.fragmentHelper = PaneFragmentManagerHelper(fragmentManager)

        // 注册中心动态变化时（如远端开关切换），重新加载
        registry.addOnChangeListener { _ -> reloadPanes() }

        // 框架自身的 Activity Lifecycle 联动可在此扩展（暂时由 FragmentManager 自动管理）
        @Suppress("UNUSED_VARIABLE") val ignored = lifecycle

        post { reloadPanes() }
    }

    @MainThread
    @PaneApi
    fun openSlot(slot: PaneSlot, animate: Boolean = true) {
        if (slot == PaneSlot.CENTER) {
            closeAll(animate); return
        }
        if (registry.get(slot) == null) return
        val target = targetCenterLeftFor(slot)
        if (animate) {
            if (dragHelper.smoothSlideViewTo(centerContainer, target, centerContainer.top)) {
                ViewCompat.postInvalidateOnAnimation(this)
            }
        } else {
            centerContainer.offsetLeftAndRight(target - centerContainer.left)
            settleStateAt(target)
        }
    }

    @MainThread
    @PaneApi
    fun closeAll(animate: Boolean = true) {
        if (animate) {
            if (dragHelper.smoothSlideViewTo(centerContainer, 0, centerContainer.top)) {
                ViewCompat.postInvalidateOnAnimation(this)
            }
        } else {
            centerContainer.offsetLeftAndRight(-centerContainer.left)
            settleStateAt(0)
        }
    }

    @PaneApi
    fun getCurrentState(): PaneState = currentState

    @PaneApi
    fun setSlideEnabled(enabled: Boolean) {
        slideEnabled = enabled
    }

    @PaneApi
    fun setSlideEnabledForSlot(slot: PaneSlot, enabled: Boolean) {
        if (slot == PaneSlot.CENTER) return
        slotEnabled[slot] = enabled
    }

    // ----- 策略替换 -----
    @PaneApi
    fun setTransitionAnimator(animator: PaneTransitionAnimator) {
        transitionAnimator = animator
        if (width > 0) {
            animator.onContainerSizeChanged(width, height)
            // 让侧 Pane 立刻应用 progress=0 的初始视差状态（避免首次未拖动前裸露）
            applyInitialAnimatorState()
        }
    }

    private fun applyInitialAnimatorState() {
        val anim = transitionAnimator ?: return
        if (registry.get(PaneSlot.START) != null) {
            anim.onSlide(centerContainer, startContainer, PaneSlot.START, 0f)
        }
        if (registry.get(PaneSlot.END) != null) {
            anim.onSlide(centerContainer, endContainer, PaneSlot.END, 0f)
        }
    }

    @PaneApi
    fun setNestedScrollPolicy(policy: NestedScrollPolicy) {
        nestedScrollPolicy = policy
    }

    @PaneApi
    fun setEdgeGestureStrategy(strategy: EdgeGestureStrategy) {
        edgeGestureStrategy = strategy
        applySystemGestureExclusion()
    }

    @PaneApi
    fun setPaneLoadingStrategy(strategy: PaneLoadingStrategy) {
        loadingStrategy = strategy
    }

    // ----- Pane 生命周期观察 -----
    @PaneApi
    fun addPaneLifecycleObserver(observer: PaneLifecycleObserver) {
        paneLifecycleObservers.add(observer)
    }

    @PaneApi
    fun removePaneLifecycleObserver(observer: PaneLifecycleObserver) {
        paneLifecycleObservers.remove(observer)
    }

    // ----- 纵向手势广播 -----
    @PaneApi
    fun addVerticalGestureObserver(observer: VerticalGestureObserver) {
        verticalBroadcaster.addObserver(observer)
    }

    @PaneApi
    fun removeVerticalGestureObserver(observer: VerticalGestureObserver) {
        verticalBroadcaster.removeObserver(observer)
    }

    @PaneApi
    @JvmOverloads
    fun addScrollDirectionListener(
        thresholdPx: Int = (12 * resources.displayMetrics.density).toInt(),
        listener: ScrollDirectionListener
    ) {
        verticalBroadcaster.addDirectionListener(thresholdPx, listener)
    }

    @PaneApi
    fun removeScrollDirectionListener(listener: ScrollDirectionListener) {
        verticalBroadcaster.removeDirectionListener(listener)
    }

    @PaneApi
    fun setVerticalGestureBroadcastEnabled(enabled: Boolean) {
        verticalBroadcaster.setEnabled(enabled)
    }

    // ==================== 内部：加载 Pane ====================

    private fun reloadPanes() {
        val helper = fragmentHelper ?: return
        val centerProvider = registry.get(PaneSlot.CENTER)
            ?: error("CENTER pane is required, please register a PaneProvider for PaneSlot.CENTER")

        // CENTER 必须预加载
        helper.ensureAttached(centerProvider, centerContainer.id)
        helper.moveToResumed(PaneSlot.CENTER, centerProvider.paneId)

        listOf(PaneSlot.START to startContainer, PaneSlot.END to endContainer)
            .forEach { (slot, container) ->
                val provider = registry.get(slot)
                if (provider != null && loadingStrategy.shouldPreload(slot)) {
                    helper.ensureAttached(provider, container.id)
                }
                applyWidthSpec(container, provider?.widthSpec() ?: PaneWidthSpec.MatchParent)
            }
    }

    private fun ensureSidePaneLoaded(slot: PaneSlot) {
        val helper = fragmentHelper ?: return
        val provider = registry.get(slot) ?: return
        val container = containerFor(slot)
        helper.ensureAttached(provider, container.id)
    }

    private fun applyWidthSpec(container: FrameLayout, spec: PaneWidthSpec) {
        val containerWidth = width
        if (containerWidth == 0) return
        val targetWidth = when (spec) {
            PaneWidthSpec.MatchParent -> containerWidth
            is PaneWidthSpec.Fraction -> (containerWidth * spec.ratio).toInt()
            is PaneWidthSpec.Fixed -> spec.widthPx
        }
        val lp = container.layoutParams
        if (lp.width != targetWidth) {
            lp.width = targetWidth
            container.layoutParams = lp
        }
    }

    // ==================== Layout ====================

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        transitionAnimator?.onContainerSizeChanged(w, h)
        applySystemGestureExclusion()
        // 重新应用宽度规格
        registry.get(PaneSlot.START)?.let { applyWidthSpec(startContainer, it.widthSpec()) }
        registry.get(PaneSlot.END)?.let { applyWidthSpec(endContainer, it.widthSpec()) }
        // 应用 animator 的初始视差状态（首次 layout / 旋转后）
        applyInitialAnimatorState()
    }

    private fun applySystemGestureExclusion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val rects = edgeGestureStrategy?.systemGestureExclusionRects(width, height) ?: emptyList()
        systemGestureExclusionRects = rects
    }

    // ==================== 触摸分发 ====================

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // 早期采样纵向手势，不影响事件正常分发
        verticalBroadcaster.observe(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!slideEnabled) return false

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                horizontalIntercepted = false
                verticalLockedToChild = false
                // 让 dragHelper 记录初始坐标（DOWN 阶段不会 captureView，仅状态同步）
                try { dragHelper.processTouchEvent(ev) } catch (_: IllegalArgumentException) {}
            }
            MotionEvent.ACTION_MOVE -> {
                if (verticalLockedToChild) return false
                if (horizontalIntercepted) return true

                val dx = ev.x - downX
                val dy = ev.y - downY
                if (abs(dx) <= touchSlop && abs(dy) <= touchSlop) {
                    // 还未越过 slop，先不做判定
                    return false
                }

                val isHorizontal = abs(dx) > abs(dy) * 1.2f && abs(dx) > touchSlop
                if (!isHorizontal) {
                    // 纵向占优：交给子 View 处理（如 RecyclerView 滚动）
                    verticalLockedToChild = true
                    return false
                }

                // 已开侧 Pane 时的反向手势 = 关闭意图，直接接管，跳过 open 相关准入检查
                val isClosingGesture = when (currentState) {
                    PaneState.START_OPEN -> dx < 0
                    PaneState.END_OPEN -> dx > 0
                    PaneState.CENTER -> false
                }

                if (!isClosingGesture) {
                    // CENTER 状态下尝试打开某槽位 → 走完整准入检查
                    val targetSlot = if (dx > 0) PaneSlot.START else PaneSlot.END
                    if (slotEnabled[targetSlot] != true || registry.get(targetSlot) == null) {
                        verticalLockedToChild = true
                        return false
                    }
                    val edgeOk = if (config.enableEdgeOnly) {
                        edgeGestureStrategy?.isEdgeAllowed(targetSlot, downX, width) ?: true
                    } else true
                    if (!edgeOk) {
                        verticalLockedToChild = true
                        return false
                    }
                    val touchedChild = findTopChildUnder(centerContainer, ev.x, ev.y)
                    val policyAllow = nestedScrollPolicy
                        ?.shouldContainerInterceptHorizontal(ev, dx, dy, touchedChild)
                        ?: true
                    if (!policyAllow) {
                        verticalLockedToChild = true
                        return false
                    }
                    ensureSidePaneLoaded(targetSlot)
                }

                horizontalIntercepted = true
                // 接管事件流：手动 capture centerContainer
                // （关闭手势场景下，centerContainer 已被推出屏幕，
                //  ViewDragHelper.shouldInterceptTouchEvent 内部的 findTopChildUnder 找不到它，
                //  必须主动 captureChildView 强制接管）
                val pointerId = ev.getPointerId(ev.actionIndex)
                dragHelper.captureChildView(centerContainer, pointerId)
                return true
            }
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_UP -> {
                horizontalIntercepted = false
                verticalLockedToChild = false
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!slideEnabled) return false
        try {
            // 拦截后，所有事件交给 dragHelper 处理（包括首次 MOVE 后的 DOWN 同步）
            dragHelper.processTouchEvent(event)
        } catch (_: IllegalArgumentException) {
            // ViewDragHelper 在多指场景偶发，吞掉避免崩溃
        }
        return true
    }

    override fun computeScroll() {
        if (dragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this)
        }
    }

    // ==================== ViewDragHelper 回调 ====================

    private inner class DragCallback : ViewDragHelper.Callback() {

        override fun tryCaptureView(child: View, pointerId: Int): Boolean {
            return child === centerContainer && slideEnabled
        }

        override fun getViewHorizontalDragRange(child: View): Int = width

        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int {
            val maxRight = if (registry.get(PaneSlot.START) != null && slotEnabled[PaneSlot.START] == true) width else 0
            val maxLeft = if (registry.get(PaneSlot.END) != null && slotEnabled[PaneSlot.END] == true) -width else 0
            return min(maxRight, max(maxLeft, left))
        }

        override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int = 0

        override fun onViewPositionChanged(
            changedView: View,
            left: Int,
            top: Int,
            dx: Int,
            dy: Int
        ) {
            updateSidePanesAndAnimate(left, isUserDragging = dragHelper.viewDragState == ViewDragHelper.STATE_DRAGGING)
        }

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            val target = decideSettleTarget(releasedChild.left, xvel)
            if (dragHelper.settleCapturedViewAt(target, releasedChild.top)) {
                ViewCompat.postInvalidateOnAnimation(this@SlidePaneContainer)
            }
        }

        override fun onViewDragStateChanged(state: Int) {
            if (state == ViewDragHelper.STATE_IDLE) {
                settleStateAt(centerContainer.left)
            }
        }
    }

    // ==================== 状态判定与回调 ====================

    private fun decideSettleTarget(currentLeft: Int, xvel: Float): Int {
        val velocityThresholdPx = config.openVelocityThresholdDpPerSecond * resources.displayMetrics.density
        val widthF = width.toFloat()
        val ratio = currentLeft / widthF
        val startAvailable = registry.get(PaneSlot.START) != null && slotEnabled[PaneSlot.START] == true
        val endAvailable = registry.get(PaneSlot.END) != null && slotEnabled[PaneSlot.END] == true

        // 基于速度判定：速度方向只决定"朝哪边 settle"，目标位置由当前位置 + 速度方向共同决定，
        // 必须停靠到"当前位置在速度方向上最近的合法停靠点"，绝不能越过中心位飞到对侧。
        if (abs(xvel) > velocityThresholdPx) {
            return when {
                // 向右滑（xvel > 0）
                xvel > 0 -> when {
                    currentLeft < 0 -> 0                        // END_OPEN 区间 → 关回 CENTER
                    currentLeft >= 0 && startAvailable -> width // CENTER 或 START 区间 → 打开/保持 START
                    else -> 0
                }
                // 向左滑（xvel < 0）
                xvel < 0 -> when {
                    currentLeft > 0 -> 0                          // START_OPEN 区间 → 关回 CENTER
                    currentLeft <= 0 && endAvailable -> -width    // CENTER 或 END 区间 → 打开/保持 END
                    else -> 0
                }
                else -> 0
            }
        }
        // 基于位置判定（速度不足时按落点比例就近吸附）
        return when {
            ratio > config.openPositionThreshold && startAvailable -> width
            ratio < -config.openPositionThreshold && endAvailable -> -width
            else -> 0
        }
    }

    private fun targetCenterLeftFor(slot: PaneSlot): Int = when (slot) {
        PaneSlot.START -> width
        PaneSlot.END -> -width
        PaneSlot.CENTER -> 0
    }

    private fun updateSidePanesAndAnimate(centerLeft: Int, isUserDragging: Boolean) {
        val absLeft = abs(centerLeft)
        val fraction = if (width == 0) 0f else absLeft / width.toFloat()
        val activeSlot = when {
            centerLeft > 0 -> PaneSlot.START
            centerLeft < 0 -> PaneSlot.END
            else -> PaneSlot.CENTER
        }

        // 控制可见性
        when (activeSlot) {
            PaneSlot.START -> {
                startContainer.visibility = VISIBLE
                endContainer.visibility = INVISIBLE
            }
            PaneSlot.END -> {
                endContainer.visibility = VISIBLE
                startContainer.visibility = INVISIBLE
            }
            PaneSlot.CENTER -> {
                startContainer.visibility = INVISIBLE
                endContainer.visibility = INVISIBLE
            }
        }

        // 委托动画策略
        if (activeSlot != PaneSlot.CENTER) {
            val sideContainer = containerFor(activeSlot)
            transitionAnimator?.onSlide(centerContainer, sideContainer, activeSlot, fraction)
        }

        // 通知滑动进度
        notifySlideProgress(activeSlot, fraction, isUserDragging)
    }

    private fun settleStateAt(centerLeft: Int) {
        val newState = when (centerLeft) {
            width -> PaneState.START_OPEN
            -width -> PaneState.END_OPEN
            0 -> PaneState.CENTER
            else -> return
        }
        if (newState == currentState) return

        val previous = currentState
        currentState = newState
        applyLifecycleForState(previous, newState)
        dispatchPaneAppearance(previous, newState)
    }

    private fun applyLifecycleForState(previous: PaneState, current: PaneState) {
        val helper = fragmentHelper ?: return
        // 旧状态对应的 Pane 降级到 STARTED
        slotOf(previous)?.let { slot ->
            registry.get(slot)?.let { helper.moveToStarted(slot, it.paneId) }
        }
        // 新状态对应的 Pane 升级到 RESUMED
        slotOf(current)?.let { slot ->
            registry.get(slot)?.let { helper.moveToResumed(slot, it.paneId) }
        }
        // CENTER 始终保持至少 STARTED；当前居中态时 CENTER 是 RESUMED
        if (current == PaneState.CENTER) {
            registry.get(PaneSlot.CENTER)?.let { helper.moveToResumed(PaneSlot.CENTER, it.paneId) }
        } else {
            registry.get(PaneSlot.CENTER)?.let { helper.moveToStarted(PaneSlot.CENTER, it.paneId) }
        }
    }

    private fun dispatchPaneAppearance(previous: PaneState, current: PaneState) {
        val snapshot = paneLifecycleObservers.toList()
        slotOf(previous)?.let { slot ->
            snapshot.forEach { it.onPaneDidDisappear(slot) }
        }
        slotOf(current)?.let { slot ->
            snapshot.forEach { it.onPaneDidAppear(slot) }
        }
    }

    private fun notifySlideProgress(activeSlot: PaneSlot, fraction: Float, isUserDragging: Boolean) {
        // 进度连续变化的边界事件：WillAppear / WillDisappear
        val snapshot = paneLifecycleObservers.toList()
        if (activeSlot != lastNotifiedActiveSlot) {
            // 旧的 active 退出（WillDisappear，DidDisappear 由 settle 阶段触发）
            if (lastNotifiedActiveSlot != PaneSlot.CENTER) {
                snapshot.forEach { it.onPaneWillDisappear(lastNotifiedActiveSlot) }
            }
            // 新的 active 进入
            if (activeSlot != PaneSlot.CENTER) {
                snapshot.forEach { it.onPaneWillAppear(activeSlot) }
            }
            lastNotifiedActiveSlot = activeSlot
        }
        lastNotifiedFraction = fraction

        val progress = SlideProgress(activeSlot, fraction, isUserDragging)
        snapshot.forEach { it.onSlideProgress(progress) }
    }

    private fun slotOf(state: PaneState): PaneSlot? = when (state) {
        PaneState.START_OPEN -> PaneSlot.START
        PaneState.END_OPEN -> PaneSlot.END
        PaneState.CENTER -> null
    }

    private fun containerFor(slot: PaneSlot): FrameLayout = when (slot) {
        PaneSlot.START -> startContainer
        PaneSlot.END -> endContainer
        PaneSlot.CENTER -> centerContainer
    }

    private fun findTopChildUnder(parent: ViewGroup, x: Float, y: Float): View? {
        for (i in parent.childCount - 1 downTo 0) {
            val child = parent.getChildAt(i)
            if (child.visibility != VISIBLE) continue
            val rect = Rect()
            child.getHitRect(rect)
            if (rect.contains(x.toInt(), y.toInt())) return child
        }
        return null
    }

    // ==================== 状态保存 ====================

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        return SavedState(superState).apply {
            stateOrdinal = currentState.ordinal
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.superState)
        val restoredState = PaneState.entries.getOrNull(state.stateOrdinal) ?: PaneState.CENTER
        post {
            when (restoredState) {
                PaneState.START_OPEN -> openSlot(PaneSlot.START, animate = false)
                PaneState.END_OPEN -> openSlot(PaneSlot.END, animate = false)
                PaneState.CENTER -> closeAll(animate = false)
            }
        }
    }

    override fun dispatchSaveInstanceState(container: SparseArray<Parcelable>) {
        // 仅保存自身状态，不递归保存子容器（FragmentManager 会负责 Fragment 状态）
        super.dispatchFreezeSelfOnly(container)
    }

    override fun dispatchRestoreInstanceState(container: SparseArray<Parcelable>) {
        super.dispatchThawSelfOnly(container)
    }

    private class SavedState : BaseSavedState {
        var stateOrdinal: Int = PaneState.CENTER.ordinal

        constructor(superState: Parcelable?) : super(superState)
        constructor(source: Parcel) : super(source) {
            stateOrdinal = source.readInt()
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(stateOrdinal)
        }

        companion object {
            @JvmField
            val CREATOR = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(source: Parcel) = SavedState(source)
                override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
            }
        }
    }
}
