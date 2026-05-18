plugins {
    id("demo.android.feature")
}

android {
    namespace = "com.demo.features.feed"
}

dependencies {
    implementation(project(":foundations:common"))
    implementation(project(":foundations:ui"))
    implementation(project(":foundations:router"))
    implementation(project(":foundations:analytics"))
    implementation(project(":foundations:communicate"))

    // The whole point of this module is to dogfood AssembleKit v2:
    // ListPage + provides/consume + at(R.id.…) + Assembly.replace.
    implementation(project(":foundations:assemblekit"))

    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
