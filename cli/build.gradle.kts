import com.wire.kalium.plugins.commonDokkaConfig
import com.wire.kalium.plugins.commonJvmConfig

@Suppress("DSL_SCOPE_VIOLATION")

plugins {
    application
    kotlin("multiplatform")
}
val mainFunctionClassName = "com.wire.kalium.cli.MainKt"

application {
    mainClass.set(mainFunctionClassName)
}

kotlin {
    jvm() {
        commonJvmConfig(includeNativeInterop = false)
        tasks.named("run", JavaExec::class) {
            isIgnoreExitValue = true
            standardInput = System.`in`
            standardOutput = System.out
        }
    }
    macosX64() {
        binaries {
            executable()
        }
    }
    macosArm64() {
        binaries {
            executable()
        }
    }

    sourceSets {
        val commonMain by sourceSets.getting {
            dependencies {
                implementation(project(":network"))
                implementation(project(":cryptography"))
                implementation(project(":logic"))
                implementation(project(":util"))

                implementation(libs.cliKt)
                implementation(libs.ktor.utils)
                implementation(libs.coroutines.core)
                implementation(libs.ktxDateTime)
            }
        }
        val jvmMain by getting {
            dependsOn(commonMain)

             dependencies {
                 implementation(libs.ktor.okHttp)
                 implementation(libs.okhttp.loggingInterceptor)
             }
        }
        val darwinMain by creating {
            dependsOn(commonMain)

            dependencies {
                implementation(libs.ktor.iosHttp)
            }
        }
        val macosX64Main by getting {
            dependsOn(darwinMain)
        }
        val macosArm64Main by getting {
            dependsOn(darwinMain)
        }
    }
}

commonDokkaConfig()

tasks.withType<Wrapper> {
    gradleVersion = "7.3.1"
    distributionType = Wrapper.DistributionType.BIN
}
