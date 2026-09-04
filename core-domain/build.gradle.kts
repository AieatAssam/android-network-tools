plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    alias(libs.plugins.ktlint)
}

configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    version.set("1.7.1")
    outputToConsole.set(true)
    ignoreFailures.set(false)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core-network"))
    implementation(libs.coroutines.core)

    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.params)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(libs.junit5.launcher)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Domain rules are pure Kotlin and are inexpensive to execute in every CI
// build. Keep a meaningful floor here so new validation/use-case branches do
// not quietly land without tests, while leaving Android-only UI coverage to
// instrumented tests in :app.
kover {
    reports {
        verify {
            rule("Domain logic minimum coverage") {
                minBound(70)
            }
        }
    }
}
