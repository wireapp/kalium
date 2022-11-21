@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id(libs.plugins.android.library.get().pluginId)
    id(libs.plugins.kotlin.multiplatform.get().pluginId)
    alias(libs.plugins.kotlin.serialization)
    id(libs.plugins.carthage.get().pluginId)
}

group = "com.wire.kalium"
version = "0.0.1-SNAPSHOT"

android {
    compileSdk = Android.Sdk.compile
    defaultConfig {
        minSdk = Android.Sdk.min
        targetSdk = Android.Sdk.target
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-proguard-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        isCoreLibraryDesugaringEnabled = true
    }
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    // Remove Android Unit tests, as it's currently impossible to run native-through-NDK code on simple Unit tests.
    sourceSets.remove(sourceSets["test"])
    externalNativeBuild {
        cmake {
            version = Android.Ndk.cMakeVersion
        }
        ndkBuild {
            ndkVersion = Android.Ndk.version
            // path(File("src/androidMain/jni/Android.mk"))
        }
    }
    packagingOptions {
        jniLibs.pickFirsts.add("**/libsodium.so")
    }
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
            kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
            kotlinOptions.freeCompilerArgs += "-Xjvm-default=enable"
        }
        testRuns["test"].executionTask.configure {
            useJUnit()
            val runArgs = project.gradle.startParameter.systemPropertiesArgs.entries.map { "-D${it.key}=${it.value}" }
            jvmArgs(runArgs)
            if (System.getProperty("os.name").contains("Mac", true)) {
                jvmArgs("-Djava.library.path=/usr/local/lib/:../native/libs")
            }
        }
    }
    android() {
        dependencies {
            coreLibraryDesugaring(libs.desugarJdkLibs)
        }
    }

    js(IR) {
        browser {
            commonWebpackConfig {
                cssSupport.enabled = true
            }
            testTask {
                useMocha {
                    timeout = "5s"
                }
            }
        }
    }

    iosX64() {
        carthage {
            baseName = "Cryptography"
            dependency("WireCryptobox")
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":logger"))
                // coroutines
                implementation(libs.coroutines.core)
                api(libs.ktor.core)

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
        val androidAndroidTest by getting {
            dependencies {
                implementation(libs.androidtest.runner)
                implementation(libs.androidtest.rules)
            }
        }
    }
}
