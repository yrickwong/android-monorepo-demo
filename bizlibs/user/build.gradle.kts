plugins {
    id("demo.android.bizlib")
}

android {
    namespace = "com.demo.bizlibs.user"
}

dependencies {
    api(project(":foundations:common"))
    api(project(":bizlibs:account"))
    implementation(project(":foundations:network"))
    implementation(project(":foundations:analytics"))
    implementation(project(":third-party:logger"))
}
