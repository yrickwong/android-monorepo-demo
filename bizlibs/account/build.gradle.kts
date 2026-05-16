plugins {
    id("demo.android.bizlib")
}

android {
    namespace = "com.demo.bizlibs.account"
}

dependencies {
    api(project(":foundations:common"))
    implementation(project(":foundations:network"))
    implementation(project(":foundations:storage"))
    implementation(project(":foundations:analytics"))
    implementation(project(":third-party:logger"))
}
