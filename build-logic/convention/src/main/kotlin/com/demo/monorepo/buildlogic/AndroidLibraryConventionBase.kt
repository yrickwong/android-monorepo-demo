package com.demo.monorepo.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Shared implementation for every Android library convention plugin
 * (feature / bizlib / foundation).
 */
internal fun Project.applyAndroidLibrary(enableViewBinding: Boolean = false) {
    pluginManager.apply("com.android.library")
    pluginManager.apply("org.jetbrains.kotlin.android")

    extensions.configure<LibraryExtension> {
        configureAndroidCommon(this)
        defaultConfig {
            consumerProguardFiles("consumer-rules.pro")
        }
        if (enableViewBinding) {
            buildFeatures {
                viewBinding = true
            }
        }
    }

    addAndroidCommonDependencies()
}
