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

package com.wire.kalium.plugins

import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.register
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact

// Paths/names that are NOT part of the licensed Kalium deliverable.
// This is intentionally separate from `excludedFromCoverage` in the root build script —
// that list also contains removed modules (android, monkeys, testservice) and is keyed
// by simple project name, which collides across the :sample / :tools / :test trees.
private val SBOM_EXCLUDED_PATH_PREFIXES = listOf("sample/", "test/", "tools/")
private val SBOM_EXCLUDED_PROJECT_PATHS = setOf("data/persistence-test")

// Production runtime classpaths only — exclude test, benchmark, and metadata-helper
// configurations. The licensee receives shipping code; test deps don't ship.
// Android note: the `com.android.kotlin.multiplatform.library` plugin in this project
// exposes a single `androidRuntimeClasspath` (no Release/Main suffix). The optional
// `(Release|Main)` alternation keeps the matcher tolerant if AGP/KMP versions change
// the naming.
private val PRODUCTION_CLASSPATH_NAMES = Regex(
    "^(jvm|js)RuntimeClasspath$" +
        "|^android(Release|Main)?RuntimeClasspath$" +
        "|^(iosArm64|iosSimulatorArm64|macosArm64)MainResolvableDependenciesMetadata$"
)

private fun Project.isInSbomScope(): Boolean {
    val rel = projectDir.relativeTo(rootDir).invariantSeparatorsPath
    if (rel.isEmpty() || rel == ".") return false
    if (rel in SBOM_EXCLUDED_PROJECT_PATHS) return false
    return SBOM_EXCLUDED_PATH_PREFIXES.none { rel.startsWith(it) }
}

private fun bucketFor(configName: String): String = when {
    configName.startsWith("jvm") -> "jvm"
    configName.startsWith("android") -> "android"
    configName.startsWith("iosArm64") -> "native/iosArm64"
    configName.startsWith("iosSimulatorArm64") -> "native/iosSimulatorArm64"
    configName.startsWith("macosArm64") -> "native/macosArm64"
    configName.startsWith("js") -> "js"
    else -> "other"
}

/**
 * Registers a `collectSbomArtifacts` task on the root project that materialises every
 * third-party dependency file (jars, AARs, klibs, plus resolved npm packages and the
 * prebuilt AVS native libraries) into `build/sbom/artifacts/`. This output is the
 * input to `extractcode` + `scancode` (see `scripts/generate-sbom.sh`) for producing
 * the customer-facing file-level license/notice report.
 *
 * Must be called on the root project. Per-module copy tasks are registered lazily
 * inside `afterEvaluate` once each subproject's KMP targets and configurations are
 * known.
 */
fun Project.registerSbomCollectionTasks() {
    require(this == rootProject) {
        "registerSbomCollectionTasks() must be called on the root project"
    }

    val artifactsDir = rootProject.layout.buildDirectory.dir("sbom/artifacts")

    val aggregate = rootProject.tasks.register("collectSbomArtifacts") {
        group = "sbom"
        description = "Materialise every third-party runtime dependency under " +
            "build/sbom/artifacts/ for downstream extractcode + scancode."
    }

    subprojects.filter { it.isInSbomScope() }.forEach { sub ->
        sub.afterEvaluate {
            val matched = configurations.matching { cfg ->
                cfg.isCanBeResolved && PRODUCTION_CLASSPATH_NAMES.matches(cfg.name)
            }

            matched.forEach { cfg ->
                val bucket = bucketFor(cfg.name)
                val safeModuleId = sub.path.removePrefix(":").replace(':', '_')
                val taskName = "collectSbom_${safeModuleId}_${cfg.name}"

                val copyTask = tasks.register<Copy>(taskName) {
                    this.group = "sbom"
                    this.description = "Copy ${cfg.name} artifacts of ${sub.path} for SBOM."
                    // Lenient artifact view: a single target failing to resolve on the
                    // current host (e.g. iOS klibs on Linux CI) must not kill the run —
                    // the customer deliverable will simply omit those targets and the
                    // shell wrapper warns about it.
                    //
                    // componentFilter drops first-party project(":...") dependencies:
                    // Kalium's own modules are the deliverable, not a third party that
                    // needs notice attribution. Only external Maven/Gradle modules and
                    // file dependencies (anything that isn't a sibling project) flow
                    // through to the SBOM.
                    from(
                        cfg.incoming.artifactView {
                            lenient(true)
                            componentFilter { id -> id !is ProjectComponentIdentifier }
                        }.files
                    )
                    into(artifactsDir.map { it.dir("$bucket/$safeModuleId") })
                    duplicatesStrategy = DuplicatesStrategy.INCLUDE
                }

                aggregate.configure { dependsOn(copyTask) }
            }
        }
    }

    // JS: kotlinNpmInstall is registered on the root project by the Kotlin Gradle
    // plugin once any subproject configures a js() target. Wire it in lazily.
    rootProject.tasks.matching { it.name == "kotlinNpmInstall" }.configureEach {
        aggregate.configure { dependsOn(this@configureEach) }
    }

    aggregate.configure {
        doLast {
            val outRoot = artifactsDir.get().asFile

            // Prebuilt AVS native libraries — already extracted; copy as-is.
            val avsSrc = rootProject.file("native/libs")
            if (avsSrc.exists()) {
                avsSrc.copyRecursively(java.io.File(outRoot, "native/avs"), overwrite = true)
            }

            // Resolved npm packages: Kotlin/JS yarn places each compilation's
            // node_modules under build/js/packages/<pkg>/node_modules.
            val jsRoot = rootProject.file("build/js")
            if (jsRoot.exists()) {
                val npmDest = java.io.File(outRoot, "npm")
                jsRoot.walkTopDown()
                    .filter { it.isDirectory && it.name == "node_modules" }
                    .forEach { nodeModules ->
                        val rel = nodeModules.parentFile.relativeTo(jsRoot).path
                            .ifBlank { "root" }
                            .replace(java.io.File.separatorChar, '_')
                        nodeModules.copyRecursively(
                            java.io.File(npmDest, rel),
                            overwrite = true
                        )
                    }
            }

            // yarn.lock alongside the resolved packages, for traceability.
            val yarnLock = rootProject.file("kotlin-js-store/yarn.lock")
            if (yarnLock.exists()) {
                val dest = java.io.File(outRoot, "npm/yarn.lock")
                dest.parentFile.mkdirs()
                yarnLock.copyTo(dest, overwrite = true)
            }

            // Maven POMs for every external coordinate resolved by the in-scope
            // configurations. The <licenses> block in each POM is the
            // authoritative source for license name + URL when a package's
            // distribution doesn't bundle a LICENSE file (kotlin-stdlib,
            // atomicfu, kermit, the AndroidX AARs, etc.). Lands under
            // build/sbom/poms/<group-as-path>/<artifact>-<version>.pom for the
            // shell wrapper to parse into a TSV.
            val pomsDir = java.io.File(outRoot.parentFile, "poms")
            val moduleIds: Set<ModuleComponentIdentifier> = rootProject.subprojects
                .filter { it.isInSbomScope() }
                .flatMap { sub ->
                    sub.configurations
                        .matching {
                            it.isCanBeResolved && PRODUCTION_CLASSPATH_NAMES.matches(it.name)
                        }
                        .flatMap { cfg ->
                            runCatching {
                                cfg.incoming.resolutionResult.allComponents
                                    .map { it.id }
                                    .filterIsInstance<ModuleComponentIdentifier>()
                            }.getOrElse { emptyList() }
                        }
                }
                .toSet()

            if (moduleIds.isNotEmpty()) {
                pomsDir.mkdirs()
                val resolved = runCatching {
                    rootProject.dependencies.createArtifactResolutionQuery()
                        .forComponents(moduleIds)
                        .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)
                        .execute()
                }.getOrNull()
                resolved?.resolvedComponents?.forEach { component ->
                    val id = component.id as? ModuleComponentIdentifier ?: return@forEach
                    val groupDir = java.io.File(pomsDir, id.group.replace('.', '/'))
                    groupDir.mkdirs()
                    component.getArtifacts(MavenPomArtifact::class.java).forEach { artifact ->
                        if (artifact is ResolvedArtifactResult) {
                            artifact.file.copyTo(
                                java.io.File(groupDir, "${id.module}-${id.version}.pom"),
                                overwrite = true
                            )
                        }
                    }
                }
            }
        }
    }
}
