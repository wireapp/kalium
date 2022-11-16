import org.gradle.kotlin.dsl.kotlin
import org.gradle.kotlin.dsl.version
import org.gradle.plugin.use.PluginDependenciesSpec

object Versions {
    const val kotlin = "1.6.10"
    const val activityCompose = "1.3.1"
    const val appCompat = "1.1.0"
    const val androidPaging3 = "3.1.1"
    const val cliKt = "3.3.0"
    const val coroutines = "1.6.0-native-mt"
    const val compose = "1.1.0-rc01"
    const val composeCompiler = "1.1.0-rc02"
    const val cryptobox4j = "1.1.1"
    const val cryptoboxAndroid = "1.1.3"
    const val javaxCrypto = "1.1.0-alpha03"
    const val kover = "0.4.4"
    const val ktor = "2.0.0-beta-1"
    const val okio = "3.0.0"
    const val okHttp = "4.9.3"
    const val mockative = "1.2.6"
    const val androidWork = "2.7.1"
    const val androidTestRunner = "1.4.0"
    const val androidTestRules = "1.4.0"
    const val androidTestCore = "1.4.0"
    const val androidxArch = "2.1.0"
    const val androidxOrchestrator = "1.1.0"
    const val benAsherUUID = "0.4.0"
    const val ktxDateTime = "0.3.2"
    const val ktxSerialization = "1.3.2"
    const val multiplatformSettings = "0.8.1"
    const val androidSecurity = "1.1.0-alpha03"
    const val sqlDelight = "2.0.0-alpha01"
    const val pbandk = "0.14.1"
    const val turbine = "0.7.0"
    const val avs = "8.2.16"
    const val jna = "5.6.0"
    const val coreCrypto = "0.6.0-pre.3"
    const val desugarJdk = "1.1.5"
    const val kermit = "1.0.0"
    const val detekt = "1.19.0"
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

    fun protobuf(scope: PluginDependenciesSpec) =
        scope.id("com.google.protobuf")

    fun serialization(scope: PluginDependenciesSpec) =
        scope.kotlin("plugin.serialization") version Versions.kotlin

    fun sqlDelight(scope: PluginDependenciesSpec) =
        scope.id("app.cash.sqldelight")

    fun carthage(scope: PluginDependenciesSpec) =
        scope.id("com.wire.carthage-gradle-plugin")
}
