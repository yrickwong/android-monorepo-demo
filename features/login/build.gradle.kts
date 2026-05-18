plugins {
    id("demo.android.feature")
}

android {
    namespace = "com.demo.features.login"
}

dependencies {
    implementation(project(":foundations:common"))
    implementation(project(":foundations:ui"))
    implementation(project(":foundations:router"))
    implementation(project(":foundations:analytics"))
    implementation(project(":foundations:communicate"))
    // Page / Assembly / assemble{} DSL + transitively pulls in Mavericks.
    implementation(project(":foundations:assemblekit"))
    implementation(project(":bizlibs:account"))

    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
}
