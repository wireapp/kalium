import kotlinx.benchmark.gradle.JvmBenchmarkTarget

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.benchmark)
    alias(libs.plugins.allOpen)
    alias(libs.plugins.jhmReport)
}

group = "com.wire.kalium.benchmarks"
version = "0.0.1"

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(JavaVersion.VERSION_17.majorVersion))
    }

    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":persistence"))
                implementation(libs.coroutines.core)
                implementation(libs.ktxDateTime)
                implementation(libs.kotlinx.benchmark.runtime)
            }
        }

        val jvmMain by getting
    }
}

benchmark {
    targets {
        register("jvm") {
            this as JvmBenchmarkTarget
            jmhVersion = libs.versions.jmh.get()
        }
    }
}

jmhReport {
    val baseFolder = project.file("build/reports/benchmarks/main").absolutePath
    val lastFolder = project.file(baseFolder).list()?.sortedArray()?.lastOrNull() ?: ""
    jmhResultPath = "$baseFolder/$lastFolder/jvm.json"
    jmhReportOutput = "$baseFolder/$lastFolder"
}
