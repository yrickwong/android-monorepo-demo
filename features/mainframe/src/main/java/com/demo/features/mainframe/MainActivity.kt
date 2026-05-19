package com.demo.features.mainframe

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.demo.features.mainframe.actions.HomePaneActions
import com.demo.features.mainframe.actions.MessagesPaneActions
import com.demo.features.mainframe.actions.ProfilePaneActions
import com.demo.foundations.slidepane.PaneSlot
import com.demo.foundations.slidepane.PaneState
import com.demo.foundations.slidepane.SlidePaneContainer
import com.demo.foundations.slidepane.gesture.ScrollDirectionListener
import com.demo.foundations.slidepane.strategy.CompositeAnimator
import com.demo.foundations.slidepane.strategy.DefaultParallaxAnimator
import com.demo.foundations.slidepane.strategy.ScrimAnimator

/**
 * 三屏滑动主框架的宿主 Activity。
 *
 * 职责非常薄——三件事：
 *  1. inflate [SlidePaneContainer]，把 FragmentManager / Lifecycle 交给框架。
 *  2. 装上一组默认的视差 + 遮罩动效。
 *  3. 实现 3 个 Pane Actions 接口，把业务请求"翻译"成对 SlidePane API
 *     的调用——把 SlidePane API 当成依赖倒置的"反向接口"，避免业务
 *     Page 直接 `findContainer()` 拿 [SlidePaneContainer]。
 *
 * 注意：MainActivity 本身不是 [com.demo.foundations.assemblekit.PageHost]——
 * 它只是 SlidePane 的宿主。三个 Pane 各自有自己的 [PaneHostFragment]，每个
 * Fragment 是独立的 PageHost，每个有自己的 Shell ViewModel。这样三屏之间
 * 状态隔离、生命周期独立，符合"flat fragment + 各自 assemble"的设计。
 */
class MainActivity : AppCompatActivity(),
    HomePaneActions,
    ProfilePaneActions,
    MessagesPaneActions {

    private lateinit var container: SlidePaneContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 进程级注册：MainActivity 是 :features:mainframe 唯一的"对外入口"，
        // 把 Pane 注册放在它的 onCreate 而不是 Application#onCreate，主要是
        // 因为本工程的 :app 是干净的样例骨架，不想强制它持有
        // MainframePaneRegistration 的引用——保留把这个 module 当成"插件"
        // 加载的能力。在真实工程里推荐挪到 Application 或 ContentProvider。
        if (!registered) {
            MainframePaneRegistration.registerAll()
            registered = true
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.mainframe_activity_main)

        container = findViewById(R.id.mainframe_slide_pane_container)
        container.bind(supportFragmentManager, lifecycle)
        // 默认视差 + 中心页遮罩，模仿小红书首页交互感
        container.setTransitionAnimator(
            CompositeAnimator(
                DefaultParallaxAnimator(),
                ScrimAnimator(),
            ),
        )

        // 系统返回键：侧边 Pane 打开时优先关闭，再走默认逻辑
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (container.getCurrentState() != PaneState.CENTER) {
                    container.closeAll(animate = true)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    // ====== HomePaneActions ======
    override fun requestOpenProfile() = container.openSlot(PaneSlot.START)
    override fun requestOpenMessages() = container.openSlot(PaneSlot.END)
    override fun addScrollDirectionListener(listener: ScrollDirectionListener) {
        container.addScrollDirectionListener(listener = listener)
    }
    override fun removeScrollDirectionListener(listener: ScrollDirectionListener) {
        container.removeScrollDirectionListener(listener)
    }

    // ====== ProfilePaneActions ======
    override fun requestCloseProfile() = container.closeAll(animate = true)

    // ====== MessagesPaneActions ======
    override fun requestCloseMessages() = container.closeAll(animate = true)

    companion object {
        // 进程级幂等标志——避免同一进程重启多次注册。
        @Volatile
        private var registered: Boolean = false
    }
}
