package com.demo.monorepo.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Convention plugin applied to the **root** project.
 *
 * Registers two aggregate tasks:
 *   - `checkDependencyRules`      — enforces module-layer boundaries
 *   - `generateDependencyGraph`   — emits json / dot / html graphs
 */
class DependencyGuardConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        require(target == target.rootProject) {
            "demo.dependency.guard must be applied to the root project."
        }
        target.tasks.register(
            "checkDependencyRules",
            CheckDependencyRulesTask::class.java,
        )
        target.tasks.register(
            "generateDependencyGraph",
            GenerateDependencyGraphTask::class.java,
        )
    }
}
