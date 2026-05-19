pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "monorepo-demo"

// :app
include(":app")

// :features:*
include(":features:login")
include(":features:home")
include(":features:profile")
include(":features:feed")

// :bizlibs:*
include(":bizlibs:account")
include(":bizlibs:user")

// :foundations:*
include(":foundations:common")
include(":foundations:network")
include(":foundations:storage")
include(":foundations:router")
include(":foundations:analytics")
include(":foundations:ui")
include(":foundations:communicate")
include(":foundations:assemblekit")
include(":foundations:assemblekit-compose")

// :third-party:*
include(":third-party:logger")
