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
    const val kover = "0.4.2"
    const val ktor = "1.6.4"
    const val okHttp = "4.9.3"
    const val kotest = "4.6.3"
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
    }

    object Coroutines {
        const val core = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}"
        const val test = "org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.coroutines}"
    }

    object Cli {
        const val cliKt = "com.github.ajalt.clikt:clikt:${Versions.cliKt}"
    }

    object OkHttp {
        const val loggingInterceptor = "com.squareup.okhttp3:logging-interceptor:${Versions.okHttp}"
    }

    object Ktor {
        const val core = "io.ktor:ktor-client-core:${Versions.ktor}"
        const val json = "io.ktor:ktor-client-json:${Versions.ktor}"
        const val serialization = "io.ktor:ktor-client-serialization:${Versions.ktor}"
        const val logging = "io.ktor:ktor-client-logging:${Versions.ktor}"
        const val auth = "io.ktor:ktor-client-auth:${Versions.ktor}"
        const val webSocket = "io.ktor:ktor-client-websockets:${Versions.ktor}"

        const val mock = "io.ktor:ktor-client-mock:${Versions.ktor}"

        const val okHttp = "io.ktor:ktor-client-okhttp:${Versions.ktor}"
    }

    object Kotest {
        const val junit5Runner = "io.kotest:kotest-runner-junit5:${Versions.kotest}"
        const val assertions = "io.kotest:kotest-assertions-core:${Versions.kotest}"
        const val property = "io.kotest:kotest-property:${Versions.kotest}"
    }
}
