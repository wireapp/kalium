import com.wire.plugin.CarthagePlatform
import com.wire.plugin.CarthageParameters
import com.wire.plugin.CarthageCommand
import com.wire.plugin.DefGeneratorArtifactDefinition

plugins {
    Plugins.androidLibrary(this)
    Plugins.multiplatform(this)
    Plugins.serialization(this)
    Plugins.wire(this)
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.0")
    implementation("org.jetbrains.kotlin:kotlin-native-utils:1.6.0")
}

group = "com.wire.kalium"
version = "0.0.1-SNAPSHOT"

android {
    compileSdk = Android.Sdk.compile
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    defaultConfig {
        minSdk = Android.Sdk.min
        targetSdk = Android.Sdk.target
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    externalNativeBuild {
        cmake {
            version = Android.Ndk.cMakeVersion
        }
        ndkBuild {
            ndkVersion = Android.Ndk.version
            path(File("src/androidMain/jni/Android.mk"))
        }
    }
}

val iosCryptoboxArtifact = DefGeneratorArtifactDefinition(
    artifactName = "WireCryptobox",
    artifactTarget = "ios-x86_64-simulator",
    isXCFramework = true
)

val defsOutputDir = projectDir.resolve("defs")

wire {
    tag.set("cryptography")
    carthageParameters.set(
        CarthageParameters(
            CarthageCommand.UPDATE,
            platforms = listOf(CarthagePlatform.IOS),
            useXCFrameworks = true
        )
    )
    defGeneratorParameters.set(
        com.wire.plugin.DefGeneratorParameters(
            defOutputDir = defsOutputDir,
            artifactDefinitions = listOf(iosCryptoboxArtifact)
        )
    )
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
        testRuns["test"].executionTask.configure {
            useJUnit()
        }
    }
    android()

    iosX64 {
        compilations
            .getByName(org.jetbrains.kotlin.gradle.plugin.KotlinCompilation.MAIN_COMPILATION_NAME)
            .cinterops
            .create(iosCryptoboxArtifact.artifactName) {
                defFileProperty.set(project.rootDir.resolve("cryptography").resolve(wire.defFileForArtifact(iosCryptoboxArtifact)))
                includeDirs("${project.rootDir}/cryptography/Carthage/Build/${wire.headersDirForArtifact(iosCryptoboxArtifact).path}")
                packageName = "com.wire.${iosCryptoboxArtifact.artifactName}"
            }

//        compilations
//            .getByName(org.jetbrains.kotlin.gradle.plugin.KotlinCompilation.MAIN_COMPILATION_NAME)
//            .cinterops
//            .create("AFNetworking") {
//                defFileProperty.set(File("${project.rootDir}/cryptography/Carthage/Build/Defs/$name.def"))
//                // ios-arm64_i386_x86_64-simulator
//                // ios-x86_64-simulator
//                includeDirs("${project.rootDir}/cryptography/Carthage/Build/$name.xcframework/ios-arm64_i386_x86_64-simulator/$name.framework/Headers")
//                packageName = "com.wire.$name"
//            }

        binaries {
            framework {
                baseName = "Cryptography"
                transitiveExport = true
                linkerOpts(
                    "-F${project.rootDir}/cryptography/Carthage/Build/${iosCryptoboxArtifact.artifactName}.xcframework/${iosCryptoboxArtifact.artifactTarget}"
                )
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // coroutines
                implementation(Dependencies.Coroutines.core)

            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(Dependencies.Cryptography.cryptobox4j)
            }
        }
        val jvmTest by getting
        val androidMain by getting {
            dependencies {
                implementation(Dependencies.Cryptography.cryptoboxAndroid)
            }
        }
        val androidTest by getting
    }
}
