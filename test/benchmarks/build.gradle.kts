import kotlinx.benchmark.gradle.JvmBenchmarkTarget

plugins {
    kotlin("jvm")
    alias(libs.plugins.benchmark)
    alias(libs.plugins.allOpen)
}

allOpen { annotation("org.openjdk.jmh.annotations.State") }

kotlin {
    jvmToolchain(21)
    sourceSets.main { kotlin.srcDir("../benchmark-fixtures/kotlin") }
    compilerOptions.optIn.add("com.wire.kalium.util.DebugKaliumApi")
}

dependencies {
    implementation(projects.data.persistence)
    implementation(projects.logic)
    implementation(libs.coroutines.core)
    implementation(libs.kotlinx.benchmark.runtime)
    implementation(libs.paging.common)
}

benchmark {
    configurations {
        named("main") {
            // Timeline measurements require their dedicated opt-in configuration.
            exclude("MessageTimeline")
            iterations = 10
            warmups = 3
            iterationTime = 1
            iterationTimeUnit = "s"
            mode = "avgt"
            outputTimeUnit = "ms"
            reportFormat = "json"
            providers.gradleProperty("benchmarkFilter").orNull?.let { include(it) }
        }
        if (providers.gradleProperty("enableMessageTimelineBenchmarks").orNull == "true") {
            register("messageTimeline") {
                include("MessageTimeline")
                iterations = 10
                warmups = 3
                iterationTime = 1
                iterationTimeUnit = "s"
            }
        }
    }
    targets {
        register("main") { (this as JvmBenchmarkTarget).jmhVersion = libs.versions.jmh.get() }
    }
}
