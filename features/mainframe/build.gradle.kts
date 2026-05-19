plugins {
    id("demo.android.feature")
}

android {
    namespace = "com.demo.features.mainframe"
}

dependencies {
    implementation(project(":foundations:common"))

    // 本 feature 是 AssembleKit 在"三屏滑动主框架"场景下的 dogfood：
    // Host 用 PageHostFragment，每个 Pane 拆成多 Page，列表用 ListPage / MultiTypeListPage，
    // Shell ViewModel 用 Mavericks，Actions 通过 hostLocal + PageContextKey 注入。
    implementation(project(":foundations:assemblekit"))

    // SlidePane 容器：横向三屏拖拽，由 MainActivity 直接持有。
    implementation(project(":foundations:slidepane"))

    // Home Pane 顶部下拉刷新
    implementation(libs.androidx.swiperefreshlayout)

    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.mavericks)
}
