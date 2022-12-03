package com.wire.kalium.plugins

import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

fun KotlinJvmTarget.commonJvmConfig(includeNativeInterop: Boolean) {
    compilations.all {
        kotlinOptions.jvmTarget = "1.8"
        kotlinOptions.freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
    }
    testRuns.getByName("test").executionTask.configure {
        useJUnit()
        if (includeNativeInterop) {
            val runArgs = project.gradle.startParameter.systemPropertiesArgs.entries.map { "-D${it.key}=${it.value}" }
            jvmArgs(runArgs)
            if (System.getProperty("os.name").contains("Mac", true)) {
                jvmArgs("-Djava.library.path=/usr/local/lib/:../native/libs")
            }
        }
    }
}
