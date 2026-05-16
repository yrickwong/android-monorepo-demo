// Top-level build file shared by all sub-projects.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false

    // `checkDependencyRules` / `generateDependencyGraph` are registered by the
    // `demo.dependency.guard` convention plugin which is applied to the root
    // project here.
    id("demo.dependency.guard")
}
