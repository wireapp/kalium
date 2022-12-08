@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id(libs.plugins.android.library.get().pluginId)
    id(libs.plugins.kotlin.multiplatform.get().pluginId)
    alias(libs.plugins.kotlin.serialization)
    id(libs.plugins.kalium.library.get().pluginId)
}

kaliumLibrary {
    multiplatform {
        enableJs.set(false)
    }
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":protobuf"))
                api(project(":logger"))

                // coroutines
                implementation(libs.coroutines.core)

                // ktor
                api(libs.ktor.core)
                implementation(libs.ktor.utils)
                implementation(libs.ktor.json)
                implementation(libs.ktor.serialization)
                implementation(libs.ktor.logging)
                implementation(libs.ktor.authClient)
                implementation(libs.ktor.webSocket)
                implementation(libs.ktor.contentNegotiation)
                implementation(libs.ktor.encoding)

                // Okio
                implementation(libs.okio.core)
                implementation(libs.okio.test)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                // coroutines
                implementation(libs.coroutines.test)
                // ktor test
                implementation(libs.ktor.mock)
            }
        }

        fun org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet.addCommonKotlinJvmSourceDir() {
            kotlin.srcDir("src/commonJvmAndroid/kotlin")
        }

        val jvmMain by getting {
            addCommonKotlinJvmSourceDir()
            dependencies {
                implementation(libs.ktor.okHttp)
            }
        }
        val jvmTest by getting
        val androidMain by getting {
            addCommonKotlinJvmSourceDir()
            dependencies {
                implementation(libs.ktor.okHttp)
            }
        }
        val androidTest by getting
        val iosX64Main by getting {
            dependencies {
                implementation(libs.ktor.iosHttp)
            }
        }
    }
}
