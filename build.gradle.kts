import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

buildscript {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    dependencies {
        // Kover pins a vulnerable FreeMarker transitively in its plugin
        // classpath. Keep the coverage plugin build-only and force the patched
        // FreeMarker there; this does not add either library to app runtime.
        classpath("org.jetbrains.kotlinx:kover-gradle-plugin:0.9.11")
        classpath("org.freemarker:freemarker:2.3.35")

        // AGP currently brings these build-time libraries transitively at
        // vulnerable versions. Keep the patched resolution on the Gradle
        // classpath without adding any of them to the Android runtime graph.
        classpath("org.bouncycastle:bcpkix-jdk18on:1.86")
        classpath("org.bouncycastle:bcprov-jdk18on:1.86")
        classpath("org.bitbucket.b_c:jose4j:0.9.7")
        classpath("org.jdom:jdom2:2.0.6.1")
        classpath("org.apache.commons:commons-lang3:3.21.0")
        classpath("org.apache.httpcomponents:httpclient:4.5.14")
    }
    configurations.classpath {
        resolutionStrategy.force("org.freemarker:freemarker:2.3.35")
    }
}

// Top-level build file. Configuration common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

// AGP's Android lint tool and the ktlint CLI resolve in their own build-only
// configurations in every module, separate from the root buildscript classpath.
// Keep their vulnerable transitive requests on patched versions without adding
// these libraries to any app or test runtime configuration.
val buildToolSecurityPins = mapOf(
    "androidLintTool" to listOf(
        "org.bouncycastle:bcpkix-jdk18on:1.86",
        "org.bouncycastle:bcprov-jdk18on:1.86",
        "org.bouncycastle:bcutil-jdk18on:1.86",
        "org.apache.commons:commons-lang3:3.21.0",
        "org.apache.httpcomponents:httpclient:4.5.14",
    ),
    // ktlint 1.8.0 bundles Logback 1.3.16; the published fixes are on 1.5.x.
    "ktlint" to listOf(
        "ch.qos.logback:logback-classic:1.5.38",
        "ch.qos.logback:logback-core:1.5.38",
    ),
)

subprojects {
    configurations.configureEach {
        val configurationName = name
        buildToolSecurityPins[configurationName]?.forEach { notation ->
            this@subprojects.dependencies.constraints.add(configurationName, notation) {
                because("Keep build-only tooling on patched versions")
            }
        }
    }
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
