plugins {
    alias(libs.plugins.kotlin.jvm)
}

apply(plugin = "org.jetbrains.kotlinx.kover")

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
