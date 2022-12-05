@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id(libs.plugins.android.library.get().pluginId)
    id(libs.plugins.kotlin.multiplatform.get().pluginId)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    id(libs.plugins.kalium.library.get().pluginId)
}

kaliumLibrary {
    multiplatform {
        enableiOS.set(false)
        enableJs.set(false)
    }
}
android {
    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":network"))
                implementation(project(":cryptography"))
                implementation(project(":persistence"))
                implementation(project(":protobuf"))
                api(project(":logger"))
                api(project(":calling"))
                implementation(project(":util"))

                // coroutines
                implementation(libs.coroutines.core.map {
                    project.dependencies.create(it, closureOf<ExternalModuleDependency> {
                        version { strictly(libs.versions.coroutines.get()) }
                    })
                })
                implementation(libs.ktxSerialization)
                implementation(libs.ktxDateTime)
                implementation(libs.benAsherUUID)
                // the Dependency is duplicated between here and persistence build.gradle.kts
                implementation(libs.settings.kmp)

                // Okio
                implementation(libs.okio.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":persistence-test"))
                // coroutines
                implementation(libs.coroutines.test)
                implementation(libs.turbine)

                // mocking
                implementation(libs.mockative.runtime)
                implementation(libs.okio.test)
                implementation(libs.settings.kmpTest)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.jna)
            }
        }
        val jvmTest by getting
        val androidMain by getting {
            dependencies {
                implementation(libs.paging3)
                implementation(libs.work)
            }
        }
        val androidAndroidTest by getting {
            dependencies {
                implementation(libs.androidtest.runner)
                implementation(libs.androidtest.rules)
                implementation(libs.androidtest.orchestratorRunner)
            }
        }
    }
}

dependencies {
    configurations
        .filter { it.name.startsWith("ksp") && it.name.contains("Test") }
        .forEach {
            add(it.name, libs.mockative.processor)
        }
    androidTestUtil(libs.androidtest.orchestratorUtil)
}

ksp {
    arg("mockative.stubsUnitByDefault", "true")
}
