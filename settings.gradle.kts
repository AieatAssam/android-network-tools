pluginManagement {
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

plugins {
    // Stable dependency update reporting; see the root build script for the
    // release-only candidate filter. This plugin is settings-scoped so it also
    // sees plugin versions and the Gradle wrapper.
    id("io.github.ben-manes.versions.settings") version "0.61.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "NetSwissKnife"

include(":app")
include(":core-network")
include(":core-domain")
