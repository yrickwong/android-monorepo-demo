package com.demo.monorepo.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

/**
 * Verifies architectural layer boundaries across all modules.
 *
 * Rules enforced:
 *   - `:features:*`     → must NOT depend on another `:features:*`.
 *   - `:bizlibs:*`      → must NOT depend on any `:features:*`.
 *   - `:foundations:*`  → must NOT depend on any `:features:*` or `:bizlibs:*`.
 *   - `:third-party:*`  → must NOT depend on any business module
 *                          (`:app`, `:features:*`, `:bizlibs:*`, `:foundations:*`).
 *
 * Run with: `./gradlew checkDependencyRules`
 */
abstract class CheckDependencyRulesTask : DefaultTask() {

    init {
        group = "verification"
        description = "Checks that module-to-module dependencies respect the " +
            "architecture layering rules of the monorepo."
    }

    @TaskAction
    fun check() {
        val root = project.rootProject
        val violations = mutableListOf<String>()

        root.allprojects
            .filter { it != root }
            .forEach { module ->
                module.configurations
                    // Only inspect declared (compile-time) dependencies. We
                    // skip resolvable configurations to keep the task fast
                    // and to ignore transitive ones.
                    .filter { it.name in DECLARED_CONFIGURATIONS }
                    .forEach { config ->
                        config.dependencies
                            .filterIsInstance<org.gradle.api.artifacts.ProjectDependency>()
                            .forEach { dep ->
                                val violation = check(module, dep.dependencyProject)
                                if (violation != null) {
                                    violations += violation
                                }
                            }
                    }
            }

        if (violations.isNotEmpty()) {
            val message = buildString {
                appendLine("[checkDependencyRules] FAILED — illegal dependencies detected:")
                appendLine()
                violations.forEachIndexed { i, v ->
                    appendLine("  ${i + 1}. $v")
                }
                appendLine()
                appendLine("See docs/module-rules.md for the layering rules.")
            }
            throw GradleException(message)
        }

        logger.lifecycle("[checkDependencyRules] OK — all module dependencies are legal.")
    }

    private fun check(from: Project, to: Project): String? {
        val fromLayer = layerOf(from) ?: return null
        val toLayer = layerOf(to) ?: return null

        return when (fromLayer) {
            Layer.APP -> null // :app may depend on anything
            Layer.FEATURE -> when (toLayer) {
                Layer.FEATURE -> deny(from, to, "features must not depend on other features")
                else -> null
            }
            Layer.BIZLIB -> when (toLayer) {
                Layer.FEATURE -> deny(from, to, "bizlibs must not depend on features")
                else -> null
            }
            Layer.FOUNDATION -> when (toLayer) {
                Layer.FEATURE -> deny(from, to, "foundations must not depend on features")
                Layer.BIZLIB -> deny(from, to, "foundations must not depend on bizlibs")
                else -> null
            }
            Layer.THIRD_PARTY -> when (toLayer) {
                Layer.APP, Layer.FEATURE, Layer.BIZLIB, Layer.FOUNDATION ->
                    deny(from, to, "third-party must not depend on business modules")
                else -> null
            }
        }
    }

    private fun deny(from: Project, to: Project, reason: String): String =
        "${from.path}  →  ${to.path}   [$reason]"

    private fun layerOf(project: Project): Layer? = when {
        project.path == ":app" -> Layer.APP
        project.path.startsWith(":features:") -> Layer.FEATURE
        project.path.startsWith(":bizlibs:") -> Layer.BIZLIB
        project.path.startsWith(":foundations:") -> Layer.FOUNDATION
        project.path.startsWith(":third-party:") -> Layer.THIRD_PARTY
        else -> null
    }

    private enum class Layer { APP, FEATURE, BIZLIB, FOUNDATION, THIRD_PARTY }

    companion object {
        private val DECLARED_CONFIGURATIONS = setOf(
            "api",
            "implementation",
            "compileOnly",
            "runtimeOnly",
            "testImplementation",
            "androidTestImplementation",
            "debugImplementation",
            "releaseImplementation",
        )
    }
}
