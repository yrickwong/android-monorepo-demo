plugins {
    id("demo.android.feature")
}

android {
    namespace = "com.demo.features.home"
}

dependencies {
    implementation(project(":foundations:common"))
    implementation(project(":foundations:ui"))
    implementation(project(":foundations:router"))
    implementation(project(":foundations:analytics"))
    implementation(project(":foundations:communicate"))
    implementation(project(":bizlibs:user"))

    implementation(libs.androidx.activity.ktx)
}
