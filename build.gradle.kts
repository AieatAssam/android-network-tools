import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

// Top-level build file. Configuration common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// The settings plugin creates one aggregate report for every project and the
// settings script declares it at a fixed, stable version. Keep only release
// candidates out of the report so dependencyUpdates does not recommend alpha,
// beta, RC, or snapshot versions as production updates.
tasks.withType<DependencyUpdatesTask>().configureEach {
    revision = "release"
    gradleReleaseChannel = "current"
    rejectVersionIf {
        val stable = listOf("RELEASE", "FINAL", "GA").any { keyword ->
            candidate.version.uppercase().contains(keyword)
        } || candidate.version.matches(Regex("^[0-9,.v-]+(-r)?$"))
        !stable && !currentVersion.matches(Regex(".*[-_](alpha|beta|rc|snapshot).*", RegexOption.IGNORE_CASE))
    }
}
