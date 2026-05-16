package com.demo.monorepo.buildlogic

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Convention plugin for pure-Kotlin/JVM modules
 * (e.g. third-party adapters that don't need the Android SDK).
 */
class KotlinLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        pluginManager.apply("java-library")

        configureJavaToolchain()

        tasks.withType(KotlinCompile::class.java).configureEach {
            kotlinOptions {
                jvmTarget = JavaVersion.VERSION_17.toString()
            }
        }
    }
}
