package com.demo.features.mainframe.pages.home

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.demo.features.mainframe.R
import com.demo.features.mainframe.actions.HomePaneActionsKey
import com.demo.features.mainframe.state.HomeShellState
import com.demo.features.mainframe.state.HomeShellViewModelKey
import com.demo.foundations.assemblekit.ViewPage
import com.demo.foundations.slidepane.gesture.ScrollDirectionListener
import com.demo.foundations.slidepane.gesture.VerticalGestureEvent

/**
 * Home 底部悬浮工具栏：编辑按钮 + 搜索胶囊 + 语音按钮。
 *
 * 行为：跟随 Feed 上滑收起、下滑展开。
 *
 * MVI 链路（关键演示）：
 *  1) 框架透出滚动方向 → [scrollDirectionListener] 被回调；
 *  2) 回调把"是否收起"翻译成对 VM 的方法调用 [HomeShellViewModel.setBottomBarCollapsed]；
 *  3) VM 改 state；
 *  4) `viewModel.onEach(HomeShellState::bottomBarCollapsed)` 触发本地动画。
 *
 * 为什么不在 listener 里直接动画？——这样 config change 后会丢失"当前是否收起"，
 * 旋转屏幕后底栏的位置会跳变。把它放进 state 里，Mavericks 自动保活。
 */
internal class HomeBottomBarPage : ViewPage() {

    private var rootView: View? = null
    private var bottomBarAnimator: ValueAnimator? = null
    private var currentTranslation: Float = 0f

    private val scrollDirectionListener = ScrollDirectionListener { direction ->
        val viewModel = consume(HomeShellViewModelKey) ?: return@ScrollDirectionListener
        when (direction) {
            VerticalGestureEvent.Direction.UP -> viewModel.setBottomBarCollapsed(true)
            VerticalGestureEvent.Direction.DOWN,
            VerticalGestureEvent.Direction.IDLE -> viewModel.setBottomBarCollapsed(false)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View =
        inflater.inflate(R.layout.mainframe_page_home_bottom_bar, parent, false).also {
            rootView = it
        }

    override fun onViewCreated(view: View) {
        val actions = requireConsume(HomePaneActionsKey)
        val viewModel = requireConsume(HomeShellViewModelKey)

        // 订阅滚动方向：Page 生命周期内一直监听，detach 时自动解除（见 onDestroyView）。
        actions.addScrollDirectionListener(scrollDirectionListener)

        // 状态 → 动画
        viewModel.onEach(HomeShellState::bottomBarCollapsed) { collapsed ->
            animateBottomBar(visible = !collapsed)
        }

        // 三个按钮目前没有具体业务，仅占位演示。
    }

    override fun onDestroyView() {
        bottomBarAnimator?.cancel()
        bottomBarAnimator = null
        // 必须用 consume 而不是 requireConsume——detach 时 actions 可能已经被解除注入。
        consume(HomePaneActionsKey)?.removeScrollDirectionListener(scrollDirectionListener)
        rootView = null
    }

    private fun animateBottomBar(visible: Boolean) {
        val root = rootView ?: return
        val target = if (visible) 0f else root.height.toFloat()
        if (currentTranslation == target) return
        bottomBarAnimator?.cancel()
        bottomBarAnimator = ValueAnimator.ofFloat(currentTranslation, target).apply {
            duration = 200
            addUpdateListener { animator ->
                val v = animator.animatedValue as Float
                currentTranslation = v
                root.translationY = v
            }
            start()
        }
    }
}
