@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("com.android.kotlin.multiplatform.library")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    compilerOptions {
        optIn.add("okhttp3.internal.OkHttpInternalApi")
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

kotlin {
    jvmToolchain(21)

    jvm()

    android {
        namespace = "okhttp.okhttp3"
        compileSdk {
            version = release(37)
        }
        minSdk = 21

        androidResources {
            enable = true
        }

        optimization {
            consumerKeepRules.publish = true
            consumerKeepRules.files.add(file("okhttp3.pro"))
        }
    }

    sourceSets {
        val commonJvmAndroid =
            create("commonJvmAndroid") {
                dependsOn(commonMain.get())
                kotlin.srcDir("src/commonJvmAndroid/kotlin")

                dependencies {
                    api("com.squareup.okio:okio:3.18.1")
                    compileOnly("org.codehaus.mojo:animal-sniffer-annotations:1.27")
                }
            }

        androidMain {
            dependsOn(commonJvmAndroid)
            kotlin.srcDir("src/androidMain/kotlin")

            dependencies {
                implementation("androidx.annotation:annotation:1.10.0")
                implementation("androidx.startup:startup-runtime:1.2.0")
                compileOnly("org.conscrypt:conscrypt-openjdk-uber:2.6.2")
                compileOnly("org.bouncycastle:bcprov-jdk18on:1.85")
                compileOnly("org.bouncycastle:bcutil-jdk18on:1.85")
                compileOnly("org.bouncycastle:bctls-jdk18on:1.85")
            }
        }

        jvmMain {
            dependsOn(commonJvmAndroid)
            kotlin.srcDir("src/jvmMain/kotlin")
            resources.srcDir("src/jvmMain/resources")

            dependencies {
                compileOnly("org.conscrypt:conscrypt-openjdk-uber:2.6.2")
                compileOnly("org.bouncycastle:bcprov-jdk18on:1.85")
                compileOnly("org.bouncycastle:bcutil-jdk18on:1.85")
                compileOnly("org.bouncycastle:bctls-jdk18on:1.85")
                compileOnly("org.openjsse:openjsse:1.1.14")
                compileOnly("org.graalvm.nativeimage:svm:25.0.4")
            }
        }
    }
}
