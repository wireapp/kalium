package com.wire.kalium.plugins

import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

fun KotlinMultiplatformExtension.commonDarwinMultiplatformConfig() {
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    macosX64()

    val commonMain = sourceSets.getByName("commonMain")
    val commonTest = sourceSets.getByName("commonTest")
    val darwinMain = sourceSets.create("darwinMain") {
        dependsOn(commonMain)
    }
    val darwinTest = sourceSets.create("darwinTest") {
        dependsOn(commonTest)
    }
    val iosX64Main = sourceSets.getByName("iosX64Main") {
        dependsOn(darwinMain)
    }
    val iosX64Test = sourceSets.getByName("iosX64Test") {
        dependsOn(darwinTest)
    }
    val iosArm64Main = sourceSets.getByName("iosArm64Main") {
        dependsOn(darwinMain)
    }
    val iosArm64Test = sourceSets.getByName("iosArm64Test") {
        dependsOn(darwinTest)
    }
    val iosSimulatorArm64Main = sourceSets.getByName("iosSimulatorArm64Main") {
        dependsOn(darwinMain)
    }
    val iosSimulatorArm64Test = sourceSets.getByName("iosSimulatorArm64Test") {
        dependsOn(darwinTest)
    }
    val macosX64Main = sourceSets.getByName("macosX64Main") {
        dependsOn(darwinMain)
    }
    val macosX64Test = sourceSets.getByName("macosX64Test") {
        dependsOn(darwinTest)
    }
}
