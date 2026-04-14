import kotlinx.benchmark.gradle.JvmBenchmarkTarget
import org.gradle.api.tasks.compile.JavaCompile

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
                implementation(projects.data.persistence)
                implementation(projects.logic)
                implementation(libs.coroutines.core)
                implementation(libs.ktxDateTime)
                implementation(libs.kotlinx.benchmark.runtime)
                implementation(libs.ktor.mock)
                implementation(libs.sqldelight.androidxPaging)
            }
        }

        val jvmMain by getting
    }
}

benchmark {
    configurations {
        register("logic") {
            include("CoreLogic")
            iterations = 10
            warmups = 2
            iterationTime = 1
            iterationTimeUnit = "s"
        }
        register("persistence") {
            include("Messages")
            include("MessageRead")
        }
        register("messageRead") {
            include("MessageRead")
            iterations = 10
            warmups = 2
            iterationTime = 1
            iterationTimeUnit = "s"
        }
    }
    targets {
        register("jvm") {
            this as JvmBenchmarkTarget
            jmhVersion = libs.versions.jmh.get()
        }
    }
}

jmhReport {
    val reportsRoot = project.file("build/reports/benchmarks")
    val latestResultDir = reportsRoot
        .walkTopDown()
        .filter { it.isFile && it.name == "jvm.json" }
        .maxByOrNull { it.lastModified() }
        ?.parentFile

    val fallbackDir = reportsRoot.resolve("messageRead")
    val reportDir = latestResultDir ?: fallbackDir

    jmhResultPath = reportDir.resolve("jvm.json").absolutePath
    jmhReportOutput = reportDir.absolutePath
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(JavaVersion.VERSION_17.majorVersion.toInt())
}
