plugins {
    id("demo.android.foundation")
}

android {
    namespace = "com.demo.foundations.storage"
}

dependencies {
    implementation(project(":foundations:common"))
    implementation(project(":third-party:logger"))
}
