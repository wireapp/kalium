plugins {
    Plugins.androidLibrary(this)
    Plugins.multiplatform(this)
    Plugins.serialization(this)
    Plugins.carthage(this)
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
            //path(File("src/androidMain/jni/Android.mk"))
        }
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
            val runArgs = project.gradle.startParameter.systemPropertiesArgs.entries.map { "-D${it.key}=${it.value}" }
            jvmArgs(runArgs)
            if (System.getProperty("os.name").contains("Mac", true)) {
                jvmArgs("-Djava.library.path=/usr/local/lib/:../native/libs")
            }
        }
    }
    android() {
        dependencies {
            coreLibraryDesugaring(Dependencies.Android.desugarJdkLibs)
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
                implementation(Dependencies.Coroutines.core)
                api(Dependencies.Ktor.core)

                // Okio
                implementation(Dependencies.Okio.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(Dependencies.Coroutines.test)
                implementation(Dependencies.Test.okio)
            }
        }
        fun org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet.addCommonKotlinJvmSourceDir() {
            kotlin.srcDir("src/commonJvmAndroid/kotlin")
        }
        val jvmMain by getting {
            addCommonKotlinJvmSourceDir()
            dependencies {
                implementation(Dependencies.Cryptography.cryptobox4j)
                implementation(Dependencies.Cryptography.mlsClientJvm)
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
                implementation(Dependencies.Cryptography.cryptoboxAndroid)
                implementation(Dependencies.Cryptography.javaxCrypto)
                implementation(Dependencies.Cryptography.mlsClientAndroid)
            }
        }
        val androidAndroidTest by getting {
            dependencies {
                implementation(Dependencies.AndroidInstruments.androidTestRunner)
                implementation(Dependencies.AndroidInstruments.androidTestRules)
            }
        }
    }
}
