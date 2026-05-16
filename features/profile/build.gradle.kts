plugins {
    id("demo.android.feature")
}

android {
    namespace = "com.demo.features.profile"
}

dependencies {
    implementation(project(":foundations:common"))
    implementation(project(":foundations:ui"))
    implementation(project(":foundations:router"))
    implementation(project(":foundations:analytics"))
    implementation(project(":bizlibs:user"))

    implementation(libs.androidx.activity.ktx)
}
