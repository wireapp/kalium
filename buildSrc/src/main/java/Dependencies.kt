import org.gradle.kotlin.dsl.kotlin
import org.gradle.kotlin.dsl.version
import org.gradle.plugin.use.PluginDependenciesSpec

object Versions {
    val kotlin = KotlinVersion.CURRENT.toString()
    const val activityCompose = "1.3.1"
    const val appCompat = "1.1.0"
    const val cliKt = "3.3.0"
    const val coroutines = "1.6.0-native-mt"
    const val compose = "1.1.0-rc01"
    const val composeCompiler = "1.1.0-rc02"
    const val cryptobox4j = "1.0.0"
    const val cryptoboxAndroid = "1.1.3"
    const val kover = "0.4.4"
    const val ktor = "1.6.7"
    const val ktor2 = "2.0.0-beta-1"
    const val okHttp = "4.9.3"
    const val kotest = "4.6.3"
    const val mockative = "1.1.4"
    const val androidWork = "2.7.1"
    const val androidTestRunner = "1.4.0"
    const val androidTestRules = "1.4.0"
    const val androidTestCore = "1.4.0"
    const val androidxArch = "2.1.0"
    const val ktxSerialization = "1.3.2"
    const val multiplatformSettings = "0.8.1"
    const val androidSecurity = "1.0.0"
    const val sqlDelight = "2.0.0-SNAPSHOT"
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

    fun ksp(scope: PluginDependenciesSpec) =
        scope.id("com.google.devtools.ksp").version("1.6.10-1.0.2")

    fun kover(scope: PluginDependenciesSpec) =
        scope.id("org.jetbrains.kotlinx.kover") version Versions.kover

    fun multiplatform(scope: PluginDependenciesSpec) =
        scope.id("org.jetbrains.kotlin.multiplatform")

    fun serialization(scope: PluginDependenciesSpec) =
        scope.kotlin("plugin.serialization") version Versions.kotlin

    fun sqlDelight(scope: PluginDependenciesSpec) =
        scope.id("app.cash.sqldelight")
}

object Dependencies {

    object Kotlinx {
        const val serialization = "org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.ktxSerialization}"
    }

    object Android {
        const val appCompat = "androidx.appcompat:appcompat:${Versions.appCompat}"
        const val activityCompose = "androidx.activity:activity-compose:${Versions.activityCompose}"
        const val work = "androidx.work:work-runtime-ktx:${Versions.androidWork}"
        const val composeMaterial = "androidx.compose.material:material:${Versions.compose}"
        const val composeTooling = "androidx.compose.ui:ui-tooling:${Versions.compose}"
        const val coroutines = "org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.coroutines}"
        const val ktor = "io.ktor:ktor-client-android:${Versions.ktor}"
        const val securityCrypto = "androidx.security:security-crypto:${Versions.androidSecurity}"
    }

    object MultiplatformSettings {
        const val settings = "com.russhwolf:multiplatform-settings:${Versions.multiplatformSettings}"
        const val test = "com.russhwolf:multiplatform-settings-test:${Versions.multiplatformSettings}"
    }

    object AndroidInstruments {
        const val androidTestRunner = "androidx.test:runner:${Versions.androidTestRunner}"
        const val androidTestRules = "androidx.test:rules:${Versions.androidTestRules}"
        const val androidTestCore = "androidx.test:core:${Versions.androidTestCore}"
        const val androidxArchTesting = "androidx.arch.core:core-testing:${Versions.androidxArch}"
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

    object SqlDelight {
        const val runtime = "app.cash.sqldelight:runtime:${Versions.sqlDelight}"
        const val coroutinesExtension = "app.cash.sqldelight:coroutines-extensions:${Versions.sqlDelight}"
        const val androidDriver = "app.cash.sqldelight:android-driver:${Versions.sqlDelight}"
        const val nativeDriver = "app.cash.sqldelight:native-driver:${Versions.sqlDelight}"
        const val jvmDriver = "app.cash.sqldelight:sqlite-driver:${Versions.sqlDelight}"
        const val jsDriver = "app.cash.sqldelight:sqljs-driver:${Versions.sqlDelight}"
    }

    object Kotest {
        const val junit5Runner = "io.kotest:kotest-runner-junit5:${Versions.kotest}"
        const val assertions = "io.kotest:kotest-assertions-core:${Versions.kotest}"
        const val property = "io.kotest:kotest-property:${Versions.kotest}"
    }

    object Test {
        const val mockative = "io.mockative:mockative:${Versions.mockative}"
        const val mockativeProcessor = "io.mockative:mockative-processor:${Versions.mockative}"
    }
}
