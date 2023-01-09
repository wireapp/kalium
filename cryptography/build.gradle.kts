@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id(libs.plugins.android.library.get().pluginId)
    id(libs.plugins.kotlin.multiplatform.get().pluginId)
    alias(libs.plugins.kotlin.serialization)
    id(libs.plugins.kalium.library.get().pluginId)
}

kaliumLibrary {
    multiplatform {
        includeNativeInterop.set(true)
    }
}

android {
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
}

kotlin {
    android {
        dependencies {
            coreLibraryDesugaring(libs.desugarJdkLibs)
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":logger"))
                // coroutines
                implementation(libs.coroutines.core)
                api(libs.ktor.core)

                // KTX
                implementation(libs.ktxDateTime)

                // Okio
                implementation(libs.okio.core)

                // Libsodium
                implementation(libs.libsodiumBindingsMP)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.coroutines.test)
                implementation(libs.okio.test)
            }
        }

        fun org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet.addCommonKotlinJvmSourceDir() {
            kotlin.srcDir("src/commonJvmAndroid/kotlin")
        }

        val jvmMain by getting {
            addCommonKotlinJvmSourceDir()
            dependencies {
                implementation(libs.cryptobox4j)
                implementation(libs.coreCryptoJvm)
            }
        }
        val jvmTest by getting
        val jsMain by getting {
            dependencies {
                implementation(npm("@wireapp/cryptobox", "12.7.2", generateExternals = false))
                implementation(npm("@wireapp/store-engine", "4.9.9", generateExternals = false))
            }
        }
        val jsTest by getting
        val androidMain by getting {
            addCommonKotlinJvmSourceDir()
            dependencies {
                implementation(libs.cryptoboxAndroid)
                implementation(libs.javaxCrypto)
                implementation(libs.coreCryptoAndroid)
            }
        }

        val darwinMain by creating {
            dependsOn(commonMain)
        }
        val darwinTest by creating {
            dependsOn(commonTest)
        }
        val iosX64Main by sourceSets.getting {
            dependsOn(darwinMain)
        }
        val iosX64Test by sourceSets.getting {
            dependsOn(darwinTest)
        }
        val iosArm64Main by sourceSets.getting {
            dependsOn(darwinMain)
        }
        val iosArm64Test by sourceSets.getting {
            dependsOn(darwinTest)
        }
        val iosSimulatorArm64Main by sourceSets.getting {
            dependsOn(darwinMain)
        }
        val iosSimulatorArm64Test by sourceSets.getting {
            dependsOn(darwinTest)
        }
        val macosX64Main by sourceSets.getting {
            dependsOn(darwinMain)
        }
        val macosX64Test by sourceSets.getting {
            dependsOn(darwinTest)
        }
    }
}
