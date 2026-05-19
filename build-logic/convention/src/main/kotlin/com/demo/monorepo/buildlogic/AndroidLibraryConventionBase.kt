package com.demo.monorepo.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Shared implementation for every Android library convention plugin
 * (feature / bizlib / foundation / foundation-compose).
 *
 * `enableViewBinding` opts the module into AGP's ViewBinding code-gen.
 * `enableCompose` opts the module into Jetpack Compose — turns on the
 * `compose` buildFeature, pins the Compose compiler extension version
 * from `libs.versions.toml` (`composeCompiler`), and adds the Compose
 * BOM + the runtime/ui/foundation/tooling-preview libraries as `api`
 * so consumers of the module get them transitively.
 */
internal fun Project.applyAndroidLibrary(
    enableViewBinding: Boolean = false,
    enableCompose: Boolean = false,
) {
    pluginManager.apply("com.android.library")
    pluginManager.apply("org.jetbrains.kotlin.android")

    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

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
        if (enableCompose) {
            buildFeatures {
                compose = true
            }
            composeOptions {
                kotlinCompilerExtensionVersion =
                    libs.findVersion("composeCompiler").get().toString()
            }
        }
    }

    addAndroidCommonDependencies()

    if (enableCompose) {
        dependencies {
            val bom = libs.findLibrary("androidx.compose.bom").get()
            // platform(bom) pins every transitive Compose artifact to the
            // BOM's resolved versions, so individual libraries can stay
            // version-less in the catalog.
            add("api", platform(bom))
            add("androidTestImplementation", platform(bom))
            add("api", libs.findLibrary("androidx.compose.runtime").get())
            add("api", libs.findLibrary("androidx.compose.ui").get())
            add("api", libs.findLibrary("androidx.compose.foundation").get())
            add("api", libs.findLibrary("androidx.compose.ui.tooling.preview").get())
        }
    }
}
