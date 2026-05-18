plugins {
    id("demo.android.foundation")
}

android {
    namespace = "com.demo.foundations.assemblekit"
}

dependencies {
    // Mavericks 是这套页面框架的对外契约 (Page<S, VM> 暴露 MavericksState / MavericksViewModel)
    // 所以用 api，让 :features:* / :bizlibs:* 直接拿到。
    api(libs.mavericks)

    api(libs.androidx.lifecycle.viewmodel.ktx)
    api(libs.androidx.lifecycle.viewmodel.savedstate)
    api(libs.androidx.lifecycle.common.java8)
    api(libs.androidx.savedstate.ktx)
    api(libs.kotlinx.coroutines.android)

    // ListPage 直接以 RecyclerView 作为 Page 视图，对外暴露 ItemBinder<T> 契约
    // 所以以 api 暴露给 :features:* / :bizlibs:*。
    api(libs.androidx.recyclerview)

    implementation(project(":foundations:common"))
    implementation(project(":third-party:logger"))
}
