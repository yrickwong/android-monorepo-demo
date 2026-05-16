package com.demo.monorepo.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Convention plugin for `:features:*` modules.
 *
 * A feature is allowed to depend on `:bizlibs:*`, `:foundations:*`,
 * `:third-party:*`. It can NOT depend on another `:features:*` module
 * (enforced by `checkDependencyRules`).
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        applyAndroidLibrary(enableViewBinding = true)
    }
}
