plugins {
    id("demo.android.foundation")
}

android {
    namespace = "com.demo.foundations.ui"
}

dependencies {
    api(libs.androidx.material)
    api(libs.androidx.constraintlayout)
    implementation(project(":foundations:common"))
}
