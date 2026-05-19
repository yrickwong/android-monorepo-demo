plugins {
    id("demo.android.application")
}

android {
    namespace = "com.demo.monorepo.app"

    defaultConfig {
        applicationId = "com.demo.monorepo.app"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    // foundations
    implementation(project(":foundations:common"))
    implementation(project(":foundations:ui"))
    implementation(project(":foundations:router"))
    implementation(project(":foundations:analytics"))
    implementation(project(":foundations:network"))
    implementation(project(":foundations:storage"))
    implementation(project(":foundations:communicate"))
    // Need Mavericks.initialize() in DemoApp.onCreate; the assemblekit
    // module is the single canonical place where Mavericks is wired.
    implementation(project(":foundations:assemblekit"))

    // bizlibs
    implementation(project(":bizlibs:account"))
    implementation(project(":bizlibs:user"))

    // features
    implementation(project(":features:login"))
    implementation(project(":features:home"))
    implementation(project(":features:profile"))
    implementation(project(":features:feed"))
    implementation(project(":features:mainframe"))

    // third-party
    implementation(project(":third-party:logger"))

    implementation(libs.androidx.activity.ktx)
}
