plugins {
    id("demo.android.foundation")
}

android {
    namespace = "com.demo.foundations.slidepane"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    // 框架仅依赖 AndroidX 基础库，绝不依赖任何业务模块
    api(libs.androidx.fragment.ktx)
    // ViewDragHelper 来自 customview，用 api 暴露给业务（业务可能直接引用 PaneSlot/Animator 等公共类）
    implementation(libs.androidx.customview)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
}
