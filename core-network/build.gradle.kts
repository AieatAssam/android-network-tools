plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

apply(plugin = "org.jetbrains.kotlinx.kover")

configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    version.set(libs.versions.ktlint.get())
    android.set(true)
    baseline.set(rootProject.file("config/ktlint/core-network-baseline.xml"))
}

detekt {
    toolVersion = libs.versions.detekt.get()
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    baseline = rootProject.file("config/detekt/core-network-baseline.xml")
    parallel = false
    basePath.set(projectDir)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.coroutines.core)
    implementation(libs.dnsjava)
    implementation(libs.snmp4j)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.params)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(libs.junit5.launcher)
    testImplementation(libs.mockk)
    testImplementation(libs.mockwebserver3)
    testImplementation(libs.okhttpTls)
    testImplementation(libs.coroutines.test)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Protocol parsers and repository orchestration are JVM-testable. Enforce a
// module-level floor so coverage reports are actionable rather than merely
// informational; Android framework adapters remain outside this module.
extensions.configure<kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension> {
    reports {
        verify {
            rule("Network logic minimum coverage") {
                minBound(70)
            }
        }
    }
}
