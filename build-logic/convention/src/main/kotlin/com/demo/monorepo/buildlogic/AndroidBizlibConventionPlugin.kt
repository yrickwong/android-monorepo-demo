package com.demo.monorepo.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Convention plugin for `:bizlibs:*` modules.
 *
 * Business libraries encapsulate cross-feature business logic. They can
 * depend on `:foundations:*` and `:third-party:*`, but NOT on
 * `:features:*` (enforced by `checkDependencyRules`).
 */
class AndroidBizlibConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        applyAndroidLibrary(enableViewBinding = false)
    }
}
