@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id(libs.plugins.android.library.get().pluginId)
    id(libs.plugins.kotlin.multiplatform.get().pluginId)
    alias(libs.plugins.kotlin.serialization)
    id(libs.plugins.sqldelight.get().pluginId)
}


group = "com.wire.kalium"
version = "0.0.1-SNAPSHOT"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-native-utils:1.6.0")
}

sqldelight {
    database("UserDatabase") {
        dialect = libs.sqldelight.dialect.get().toString()
        packageName = "com.wire.kalium.persistence"
        val sourceFolderName = "db_user"
        sourceFolders = listOf(sourceFolderName)
        schemaOutputDirectory = file("src/commonMain/$sourceFolderName/schemas")
    }

    database("GlobalDatabase") {
        dialect = libs.sqldelight.dialect.get().toString()
        packageName = "com.wire.kalium.persistence"
        val sourceFolderName = "db_global"
        sourceFolders = listOf(sourceFolderName)
        schemaOutputDirectory = file("src/commonMain/$sourceFolderName/schemas")
    }
}

android {
    compileSdk = Android.Sdk.compile
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    defaultConfig {
        minSdk = Android.Sdk.min
        targetSdk = Android.Sdk.target
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-proguard-rules.pro")
    }
    // Remove Android Unit tests, as it's currently impossible to run native-through-NDK code on simple Unit tests.
    sourceSets.remove(sourceSets["test"])
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
            kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
        }
        testRuns["test"].executionTask.configure {
            useJUnit()

            testLogging {
                showStandardStreams = true
            }
        }
    }
    android()
    iosX64()
    js(IR) {
        browser {
            testTask {
                // TODO: Re-enable when JS persistence is supported
                // Removed as it's currently not implemented
                this.enabled = false
                useMocha {
                    timeout = "5s"
                }
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // coroutines
                implementation(libs.coroutines.core.map {
                    project.dependencies.create(it, closureOf<ExternalModuleDependency> {
                        version { strictly(libs.versions.coroutines.get()) }
                    })
                })

                implementation(libs.sqldelight.runtime)
                implementation(libs.sqldelight.coroutinesExtension)
                implementation(libs.sqldelight.primitiveAdapters)
                implementation(libs.ktxSerialization)
                implementation(libs.settings.kmp)
                implementation(libs.ktxDateTime)

                api(project(":logger"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                // coroutines
                implementation(libs.coroutines.test)
                implementation(libs.turbine)
                // MultiplatformSettings
                implementation(libs.settings.kmpTest)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.sqldelight.jvmDriver)
            }
        }
        val jvmTest by getting
        val jsMain by getting {
            dependencies {
                implementation(libs.sqldelight.jsDriver)
                implementation(npm("sql.js", "1.6.2"))
                implementation(devNpm("copy-webpack-plugin", "9.1.0"))
            }
        }
        val jsTest by getting
        val androidMain by getting {
            dependencies {
                implementation(libs.securityCrypto)
                implementation(libs.sqldelight.androidDriver)
                implementation(libs.paging3)
                implementation("net.zetetic:android-database-sqlcipher:4.5.0@aar")
                implementation("androidx.sqlite:sqlite:2.0.1")
            }
        }
        val androidAndroidTest by getting {
            dependencies {
                implementation(libs.androidtest.runner)
                implementation(libs.androidtest.rules)
                implementation(libs.androidtest.core)
            }
        }

        val iosX64Main by getting {
            dependencies {
                implementation(libs.sqldelight.nativeDriver)
            }
        }
        val iosX64Test by getting
    }
}
