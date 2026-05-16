plugins {
    id("demo.android.foundation")
}

android {
    namespace = "com.demo.foundations.analytics"
}

dependencies {
    implementation(project(":foundations:common"))
    implementation(project(":third-party:logger"))
}
