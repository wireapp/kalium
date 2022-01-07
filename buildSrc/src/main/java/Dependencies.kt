import org.gradle.kotlin.dsl.kotlin
import org.gradle.kotlin.dsl.version
import org.gradle.plugin.use.PluginDependenciesSpec

object Versions {
    val kotlin = KotlinVersion.CURRENT.toString()
    const val activityCompose = "1.3.1"
    const val appCompat = "1.1.0"
    const val cliKt = "3.3.0"
    const val coroutines = "1.6.0-RC"
    const val compose = "1.1.0-beta04"
    const val cryptobox4j = "1.0.0"
    const val cryptoboxAndroid = "1.1.3"
    const val kover = "0.4.2"
    const val ktor = "1.6.7"
    const val ktor2 = "2.0.0-beta-1"
    const val okHttp = "4.9.3"
    const val kotest = "4.6.3"
    const val androidTestRunner = "1.4.0"
    const val androidTestRules = "1.4.0"
}

object Plugins {
    object Kotlin {
        const val jvm = "jvm"
        const val serialization = "plugin.serialization"
    }

    fun androidApplication(scope: PluginDependenciesSpec) =
        scope.id("com.android.application")

    fun androidLibrary(scope: PluginDependenciesSpec) =
        scope.id("com.android.library")

    fun androidKotlin(scope: PluginDependenciesSpec) =
        scope.kotlin("android")

    fun jvm(scope: PluginDependenciesSpec) =
        scope.kotlin("jvm")

    fun kover(scope: PluginDependenciesSpec) =
        scope.id("org.jetbrains.kotlinx.kover") version Versions.kover

    fun multiplatform(scope: PluginDependenciesSpec) =
        scope.id("org.jetbrains.kotlin.multiplatform")

    fun serialization(scope: PluginDependenciesSpec) =
        scope.kotlin("plugin.serialization") version Versions.kotlin
}

object Dependencies {

    object Android {
        const val appCompat = "androidx.appcompat:appcompat:${Versions.appCompat}"
        const val activityCompose = "androidx.activity:activity-compose:${Versions.activityCompose}"
        const val composeMaterial = "androidx.compose.material:material:${Versions.compose}"
        const val composeTooling = "androidx.compose.ui:ui-tooling:${Versions.compose}"
        const val coroutines = "org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.coroutines}"
        const val ktor = "io.ktor:ktor-client-android:${Versions.ktor}"
    }

    object AndroidInstruments {
        const val androidTestRunner = "androidx.test:runner:${Versions.androidTestRunner}"
        const val androidTestRules = "androidx.test:rules:${Versions.androidTestRules}"
    }

    object Coroutines {
        const val core = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}"
        const val test = "org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.coroutines}"
    }

    object Cryptography {
        const val cryptoboxAndroid = "com.wire:cryptobox-android:${Versions.cryptoboxAndroid}"
        const val cryptobox4j = "com.wire:cryptobox4j:${Versions.cryptobox4j}"
    }

    object Cli {
        const val cliKt = "com.github.ajalt.clikt:clikt:${Versions.cliKt}"
    }

    object OkHttp {
        const val loggingInterceptor = "com.squareup.okhttp3:logging-interceptor:${Versions.okHttp}"
    }

    object Ktor {
        const val core = "io.ktor:ktor-client-core:${Versions.ktor2}"
        const val json = "io.ktor:ktor-client-json:${Versions.ktor2}"
        const val serialization = "io.ktor:ktor-serialization-kotlinx-json:${Versions.ktor2}"
        const val logging = "io.ktor:ktor-client-logging:${Versions.ktor2}"
        const val authClient = "io.ktor:ktor-client-auth:${Versions.ktor2}"
        const val contentNegotiation = "io.ktor:ktor-client-content-negotiation:${Versions.ktor2}"
        const val webSocket = "io.ktor:ktor-client-websockets:${Versions.ktor2}"
        const val utils = "io.ktor:ktor-utils:${Versions.ktor2}"
        const val mock = "io.ktor:ktor-client-mock:${Versions.ktor2}"
        const val okHttp = "io.ktor:ktor-client-okhttp:${Versions.ktor2}"
    }

    object Kotest {
        const val junit5Runner = "io.kotest:kotest-runner-junit5:${Versions.kotest}"
        const val assertions = "io.kotest:kotest-assertions-core:${Versions.kotest}"
        const val property = "io.kotest:kotest-property:${Versions.kotest}"
    }
}
