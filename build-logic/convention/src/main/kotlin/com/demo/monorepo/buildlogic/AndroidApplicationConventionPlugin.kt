package com.demo.monorepo.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType

/**
 * Convention plugin applied to `:app`.
 * Equivalent to `id("demo.android.application")`.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.android")

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

        extensions.configure<ApplicationExtension> {
            configureAndroidCommon(this)
            defaultConfig {
                targetSdk = libs.findVersion("targetSdk").get().toString().toInt()
                versionCode = 1
                versionName = "1.0.0"
            }
            buildFeatures {
                viewBinding = true
            }
        }

        addAndroidCommonDependencies()
    }
}
