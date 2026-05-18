plugins {
    id("demo.android.foundation.compose")
}

android {
    namespace = "com.demo.foundations.assemblekit.compose"
}

dependencies {
    // The Compose flavour of the Page contract — `ComposablePage` is a
    // `Page` subclass that renders via a `ComposeView`. Consumers depend on
    // this module *in addition to* :foundations:assemblekit so they see the
    // base Page/PageContext/Assembly API as well.
    api(project(":foundations:assemblekit"))

    // mavericks-compose pulls in mavericks-core transitively and adds
    // `mavericksActivityViewModel` / `mavericksViewModel` @Composable
    // helpers. Exposed as api so subclasses of ComposablePage can pick a
    // VM directly inside Content() without re-declaring the dependency.
    api(libs.mavericks.compose)

    // Compose BOM + runtime/ui/foundation/tooling-preview are already
    // applied as `api` by the convention plugin (see
    // AndroidLibraryConventionBase.applyAndroidLibrary(enableCompose = true)),
    // so we don't repeat them here.
}
