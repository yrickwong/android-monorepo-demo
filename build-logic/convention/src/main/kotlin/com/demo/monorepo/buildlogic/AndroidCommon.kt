package com.demo.monorepo.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Shared Android configuration applied by every Android convention plugin.
 *
 * Keeps `compileSdk`, `minSdk`, Kotlin options, test options and similar
 * settings out of individual module build scripts.
 */
internal fun Project.configureAndroidCommon(
    commonExtension: CommonExtension<*, *, *, *, *>,
) {
    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

    commonExtension.apply {
        compileSdk = libs.findVersion("compileSdk").get().toString().toInt()

        defaultConfig {
            minSdk = libs.findVersion("minSdk").get().toString().toInt()
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        testOptions {
            unitTests.isIncludeAndroidResources = true
            unitTests.isReturnDefaultValues = true
        }
    }

    extensions.configure<KotlinAndroidProjectExtension> {
        jvmToolchain(17)
    }

    tasks.withType(KotlinCompile::class.java).configureEach {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_17.toString()
            freeCompilerArgs = freeCompilerArgs + listOf(
                "-opt-in=kotlin.RequiresOptIn",
            )
        }
    }
}

/**
 * Common dependencies shared by every Android module:
 *   - core-ktx / appcompat / lifecycle
 *   - junit / espresso for tests
 */
internal fun Project.addAndroidCommonDependencies() {
    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
    dependencies {
        add("implementation", libs.findLibrary("androidx.core.ktx").get())
        add("implementation", libs.findLibrary("androidx.appcompat").get())
        add("implementation", libs.findLibrary("androidx.lifecycle.runtime.ktx").get())

        add("testImplementation", libs.findLibrary("junit").get())
        add("androidTestImplementation", libs.findLibrary("androidx.junit").get())
        add("androidTestImplementation", libs.findLibrary("androidx.espresso.core").get())
    }
}

internal fun Project.configureJavaToolchain() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
