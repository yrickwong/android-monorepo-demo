package com.demo.monorepo.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Convention plugin for `:foundations:*` modules.
 *
 * Foundations are platform-level capabilities (network, storage, router,
 * analytics, ui...). They can only depend on other `:foundations:*` and
 * `:third-party:*` (enforced by `checkDependencyRules`).
 */
class AndroidFoundationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        applyAndroidLibrary(enableViewBinding = false)
    }
}
