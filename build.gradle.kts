/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension

buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:${libs.versions.agp.get()}")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
        classpath("app.cash.sqldelight:gradle-plugin:${libs.versions.sqldelight.get()}")
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:${libs.versions.dokka.get()}")
        classpath("com.google.protobuf:protobuf-gradle-plugin:${libs.versions.protobufCodegen.get()}")
        classpath("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:${libs.versions.detekt.get()}")
    }
}

repositories {
    wireDetektRulesRepo()
    google()
    mavenCentral()
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
}

plugins {
    id("org.jetbrains.dokka")
    alias(libs.plugins.kover)
    id("scripts.testing")
    id("scripts.detekt")
    alias(libs.plugins.moduleGraph)
    alias(libs.plugins.dagCommand)
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.jetbrains) apply false
}

dependencies {
    dokkaHtmlPlugin("org.jetbrains.dokka:kotlin-as-java-plugin:${libs.versions.dokka.get()}")
}

tasks.withType<Test> {
    useJUnitPlatform {
        reports.junitXml.required.set(true)
    }
}

// Workaround for Kotlin Native test report writing issue
// For some reason xml and html generation is failing, looks like tests running in parallel
subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest>().configureEach {
        reports.junitXml.required.set(false)
        reports.html.required.set(false)
        testLogging {
            events("started", "passed","failed")
            showStandardStreams = true
            }
    }

    // Configure GC for iOS Simulator ARM64 tests only
    pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
            targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>()
                .matching { it.name == "iosSimulatorArm64" }
                .configureEach {
                    binaries.all {
                        if (this is org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable) {
                            binaryOptions["gc"] = "stwms"
                        }
                    }
                }
        }
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

dagCommand {
    defaultBranch = "origin/develop"
    outputType = "json"
    printModulesInfo = true
}

kover {
    useJacoco()
}
koverReport {
    filters {
        includes {
            packages("com.wire.kalium")
        }
    }
}

fun Project.configureKover() {
    pluginManager.apply("org.jetbrains.kotlinx.kover")

    kover {
        useJacoco()
    }
    koverReport {
        filters {
            includes {
                packages("com.wire.kalium")
            }
        }
    }
}

// We only want coverage reports of actual Kalium
// Samples and other side-projects can have their own rules
val modulesWithKover = subprojects.filter {
    it.name !in setOf("buildSrc", "monkeys", "testservice", "cli", "android")
}
modulesWithKover.forEach {
    it.configureKover()
}
dependencies {
    modulesWithKover.forEach {
        kover(project(it.path))
    }
}

rootProject.plugins.withType(org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin::class.java) {
    // For unknown reasons, yarn.lock checks are failing on Github Actions
    // Considering JS support is quite experimental for us, we can live with this for now
    rootProject.the<YarnRootExtension>().yarnLockMismatchReport =
        YarnLockMismatchReport.WARNING
}

rootProject.plugins.withType<NodeJsPlugin> {
    rootProject.the<NodeJsEnvSpec>().version = "18.18.0"
    // If we want to use the downloaded Node instead of system Node:
    // rootProject.the<NodeJsEnvSpec>().download.set(true)
}

tasks.dokkaHtmlMultiModule.configure {}

moduleGraphConfig {
    readmePath.set("./README.md")
    heading.set("#### Dependency Graph")
    nestingEnabled.set(true)
    rootModulesRegex.set(":logic")
    setStyleByModuleType.set(true)
    showFullPath.set(true)
}

tasks.register("runAllUnitTests") {
    description = "Runs all Unit Tests."

    rootProject.subprojects {
        if (tasks.findByName("testDebugUnitTest") != null) {
            dependsOn(":$name:testDebugUnitTest")
        }
        if (name != "cryptography") {
            if (tasks.findByName("jvmTest") != null) {
                dependsOn(":$name:jvmTest")
            }
        }
    }
}

tasks.register("aggregateTestResults") {
    description = "Aggregates all Unit Test results into a single report."

    doLast {
        val testResultsDir = rootProject.layout.buildDirectory.dir("testResults").get().asFile
        testResultsDir.deleteRecursively()
        testResultsDir.mkdirs()

        val indexHtmlFile = File(testResultsDir, "index.html")
        indexHtmlFile.writeText(
            """
            <html>
            <head>
                <title>Aggregated Test Reports</title>
            </head>
            <body>
                <h1>Aggregated Test Reports</h1>
                <ul>
        """.trimIndent()
        )

        rootProject.subprojects {
            val testResultsParentDir = layout.buildDirectory.dir("reports/tests").get().asFile

            if (testResultsParentDir.exists()) {
                testResultsParentDir.listFiles()?.forEach { testDir ->
                    if (testDir.isDirectory) {
                        val subprojectDir = File(testResultsDir, "$name/${testDir.name}")
                        subprojectDir.mkdirs()

                        testDir.copyRecursively(subprojectDir, overwrite = true)

                        indexHtmlFile.appendText(
                            """
                            <li><a href="./$name/${testDir.name}/index.html">$name - ${testDir.name} Report</a></li>
                        """.trimIndent()
                        )
                    }
                }
            }
        }

        indexHtmlFile.appendText(
            """
                </ul>
            </body>
            </html>
        """.trimIndent()
        )

        // Print the location of the aggregated test results directory
        // relative to the current terminal working dir
        val currentWorkingDir = File(System.getProperty("user.dir"))
        val relativePath = testResultsDir.relativeTo(currentWorkingDir).path
        println("Aggregated test reports are available at: $relativePath")
    }
}

tasks.wrapper {
    distributionType = Wrapper.DistributionType.ALL
}

/*
SelfConversationIdProviderTest
E2EIClientProviderTest
IsAllowedToUseAsyncNotificationsUseCaseTest
IsWireCellsEnabledForConversationUseCaseTest
CustomServerConfigRepositoryTest
ServerConfigMapperTest
ServerConfigRepositoryTest
ServerConfigTest
WrapApiRequestTest
WrapMLSRequestTest
WrapProteusRequestTest
AnalyticsRepositoryTest
AssetMapperTest
AssetMimeTypeTest
AssetRepositoryTest
DomainRegistrationMapperTest
LoginRepositoryTest
SSOLoginRepositoryTest
SecondFactorVerificationRepositoryTest
BackupDataSourceTest
CallHelperTest
CallMapperTest
CallRepositoryTest
CallingParticipantsOrderTest
InCallReactionsRepositoryTest
ParticipantMapperTest
ParticipantsFilterTest
ParticipantsOrderByNameTest
VideoStateCheckerTest
ClientRemoteRepositoryTest
ClientRepositoryTest
ClientTest
CryptoTransactionProviderCrashTest
MLSClientProviderTest
ConnectionMapperTest
ConnectionRepositoryTest
 */
