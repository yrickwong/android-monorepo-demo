plugins {
    `kotlin-dsl`
}

group = "com.demo.monorepo.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "demo.android.application"
            implementationClass = "com.demo.monorepo.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidFeature") {
            id = "demo.android.feature"
            implementationClass = "com.demo.monorepo.buildlogic.AndroidFeatureConventionPlugin"
        }
        register("androidBizlib") {
            id = "demo.android.bizlib"
            implementationClass = "com.demo.monorepo.buildlogic.AndroidBizlibConventionPlugin"
        }
        register("androidFoundation") {
            id = "demo.android.foundation"
            implementationClass = "com.demo.monorepo.buildlogic.AndroidFoundationConventionPlugin"
        }
        register("kotlinLibrary") {
            id = "demo.kotlin.library"
            implementationClass = "com.demo.monorepo.buildlogic.KotlinLibraryConventionPlugin"
        }
        register("dependencyGuard") {
            id = "demo.dependency.guard"
            implementationClass = "com.demo.monorepo.buildlogic.DependencyGuardConventionPlugin"
        }
    }
}
