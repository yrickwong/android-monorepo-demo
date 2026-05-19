package com.demo.monorepo.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Convention plugin for `:foundations:*` modules that need Jetpack Compose.
 *
 * Identical to [AndroidFoundationConventionPlugin] for layering purposes —
 * the module still belongs to the FOUNDATION layer and the
 * `checkDependencyRules` task treats it the same way — but additionally
 * turns on Compose (`buildFeatures.compose = true`), pins the Compose
 * compiler extension version, and exposes the Compose BOM + core artifacts
 * as `api`.
 *
 * Today the sole consumer is `:foundations:assemblekit-compose`, which
 * provides the `ComposablePage` flavour of the AssembleKit page contract.
 * Any future foundation that genuinely needs Compose (e.g. a Compose-based
 * design-system shell) should also use this plugin instead of opting in
 * ad-hoc in its own `build.gradle.kts`, so the Compose version, compiler
 * extension and BOM stay coordinated through a single source of truth.
 */
class AndroidFoundationComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        applyAndroidLibrary(enableCompose = true)
    }
}
