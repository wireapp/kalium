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

import com.github.packageurl.PackageURL
import org.cyclonedx.Version
import org.cyclonedx.generators.BomGeneratorFactory
import org.cyclonedx.model.Bom
import org.cyclonedx.model.Component
import org.cyclonedx.model.Dependency
import org.cyclonedx.model.Hash
import org.cyclonedx.model.Metadata
import org.cyclonedx.model.Property
import org.cyclonedx.parsers.BomParserFactory
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.register
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import java.io.File
import java.security.MessageDigest

// Paths/names that are NOT part of the licensed Kalium deliverable.
// This is intentionally separate from `excludedFromCoverage` in the root build script —
// that list also contains removed modules (android, monkeys, testservice) and is keyed
// by simple project name, which collides across the :sample / :tools / :test trees.
private val SBOM_EXCLUDED_PATH_PREFIXES = listOf("sample/", "test/", "tools/")
private val SBOM_EXCLUDED_PROJECT_PATHS = setOf("data/persistence-test")

// Production runtime classpaths to mine, listed by explicit name. The Kotlin
// Multiplatform plugin creates the Apple target configurations lazily — they
// aren't present in the live `configurations` container until something looks
// them up by name. `findByName` forces that realization; a regex-based
// `configurations.matching {}.forEach {}` silently misses them and leaves the
// SBOM without any iOS/macOS coverage.
//
// Android: the `com.android.kotlin.multiplatform.library` plugin in this project
// exposes a single `androidRuntimeClasspath`. Older AGP/KMP combinations used
// `androidRelease`/`androidMain` variants; we list all three so a plugin
// version bump doesn't silently drop the Android bucket.
private val SBOM_TARGET_CONFIG_NAMES = listOf(
    "jvmRuntimeClasspath",
    "androidRuntimeClasspath",
    "androidReleaseRuntimeClasspath",
    "androidMainRuntimeClasspath",
    "iosArm64MainResolvableDependenciesMetadata",
    "iosSimulatorArm64MainResolvableDependenciesMetadata",
    "macosArm64MainResolvableDependenciesMetadata",
    "jsRuntimeClasspath",
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

private data class CycloneDxComponent(
    val group: String,
    val name: String,
    val version: String,
    val configurations: MutableSet<String> = sortedSetOf(),
    val artifacts: MutableSet<File> = sortedSetOf(compareBy(File::getAbsolutePath)),
)

private fun ModuleComponentIdentifier.packageUrl(): String = PackageURL(
    "maven",
    group,
    module,
    version,
    null,
    null,
).toString()

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

private fun Project.writeFastSbomFragment(configurations: List<Configuration>, output: File) {
    val components = sortedMapOf<String, CycloneDxComponent>()
    val edges = sortedMapOf<String, MutableSet<String>>()

    configurations.forEach { configuration ->
        val bucket = bucketFor(configuration.name)
        val seen = mutableSetOf<Pair<String, String>>()
        fun visit(component: ResolvedComponentResult, parentRef: String) {
            component.dependencies
                .filterIsInstance<ResolvedDependencyResult>()
                .forEach { dependency ->
                    val selected = dependency.selected
                    val moduleId = selected.id as? ModuleComponentIdentifier
                    val nextParent = if (moduleId == null) {
                        parentRef
                    } else {
                        val ref = moduleId.packageUrl()
                        components.getOrPut(ref) {
                            CycloneDxComponent(moduleId.group, moduleId.module, moduleId.version)
                        }.configurations += bucket
                        edges.getOrPut(parentRef) { sortedSetOf() } += ref
                        ref
                    }
                    val visitKey = selected.id.displayName to nextParent
                    if (seen.add(visitKey)) visit(selected, nextParent)
                }
        }
        visit(configuration.incoming.resolutionResult.root, "ROOT")

        configuration.incoming.artifactView {
            lenient(true)
            componentFilter { id -> id !is ProjectComponentIdentifier }
        }.artifacts.artifacts.forEach { artifact ->
            val moduleId = artifact.id.componentIdentifier as? ModuleComponentIdentifier
                ?: return@forEach
            val ref = moduleId.packageUrl()
            components.getOrPut(ref) {
                CycloneDxComponent(moduleId.group, moduleId.module, moduleId.version)
            }.apply {
                this.configurations += bucket
                artifacts += artifact.file
            }
        }
    }

    output.parentFile.mkdirs()
    output.bufferedWriter().use { writer ->
        components.forEach { (ref, component) ->
            component.configurations.forEach { bucket ->
                writer.appendLine(
                    listOf("C", ref, component.group, component.name, component.version, bucket)
                        .joinToString("\t"),
                )
            }
            component.artifacts.forEach { artifact ->
                writer.appendLine(listOf("A", ref, artifact.absolutePath).joinToString("\t"))
            }
        }
        edges.forEach { (parent, children) ->
            children.forEach { child ->
                writer.appendLine(listOf("E", parent, child).joinToString("\t"))
            }
        }
    }
}

private fun Project.writeFastSbomPoms(configurations: List<Configuration>, outputDir: File) {
    val moduleIds = configurations.flatMap { configuration ->
        configuration.incoming.resolutionResult.allComponents
            .map { it.id }
            .filterIsInstance<ModuleComponentIdentifier>()
    }.toSet()
    if (moduleIds.isEmpty()) return

    dependencies.createArtifactResolutionQuery()
        .forComponents(moduleIds)
        .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)
        .execute()
        .resolvedComponents
        .forEach { component ->
            val id = component.id as? ModuleComponentIdentifier ?: return@forEach
            component.getArtifacts(MavenPomArtifact::class.java)
                .filterIsInstance<ResolvedArtifactResult>()
                .forEach { artifact ->
                    val groupDir = File(outputDir, id.group.replace('.', '/'))
                    groupDir.mkdirs()
                    artifact.file.copyTo(
                        File(groupDir, "${id.module}-${id.version}.pom"),
                        overwrite = true,
                    )
                }
        }
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
        // Nested afterEvaluate: the outer block fires after the subproject's
        // own evaluation; the inner block is enqueued at that point and runs
        // AFTER any other afterEvaluate blocks the KMP plugin registered
        // during the subproject's main evaluation. The Apple target
        // configurations (e.g. iosArm64MainResolvableDependenciesMetadata) are
        // created inside one of those KMP afterEvaluate callbacks, so a single
        // afterEvaluate registered at apply-time runs too early and findByName
        // returns null. Two levels of afterEvaluate let our work land last.
        sub.afterEvaluate {
            sub.afterEvaluate {
                val safeModuleId = sub.path.removePrefix(":").replace(':', '_')
                val fastConfigurations = SBOM_TARGET_CONFIG_NAMES
                    .mapNotNull(sub.configurations::findByName)
                    .filter(Configuration::isCanBeResolved)

                if (fastConfigurations.isNotEmpty()) {
                    val fragmentFile = rootProject.layout.buildDirectory.file(
                        "sbom-fast/fragments/$safeModuleId.tsv",
                    )
                    val pomsDir = rootProject.layout.buildDirectory.dir(
                        "sbom-fast/poms/$safeModuleId",
                    )
                    tasks.register("collectFastSbom_$safeModuleId") {
                        group = "sbom"
                        description = "Collect resolved dependency graph metadata of ${sub.path}."
                        inputs.files(
                            fastConfigurations.map { configuration ->
                                configuration.incoming.artifactView {
                                    lenient(true)
                                    componentFilter { id -> id !is ProjectComponentIdentifier }
                                }.files
                            },
                        ).withPropertyName("runtimeArtifacts")
                        outputs.file(fragmentFile)
                        outputs.dir(pomsDir)
                        doFirst {
                            pomsDir.get().asFile.deleteRecursively()
                        }
                        doLast {
                            sub.writeFastSbomFragment(fastConfigurations, fragmentFile.get().asFile)
                            sub.writeFastSbomPoms(fastConfigurations, pomsDir.get().asFile)
                        }
                    }
                }

                SBOM_TARGET_CONFIG_NAMES.forEach { configName ->
                    // findByName forces realization of lazy KMP target configurations.
                    // null means this module simply doesn't declare that target.
                    val cfg = configurations.findByName(configName) ?: return@forEach
                    if (!cfg.isCanBeResolved) return@forEach

                    val bucket = bucketFor(configName)
                    val taskName = "collectSbom_${safeModuleId}_$configName"

                    tasks.register<Copy>(taskName) {
                        this.group = "sbom"
                        this.description = "Copy $configName artifacts of ${sub.path} for SBOM."
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
                }
            }
        }
    }

    aggregate.configure {
        // Wire per-module copy tasks (registered lazily inside the afterEvaluate
        // above) plus kotlinNpmInstall via live name-based matching. Declared
        // once at top level rather than mutating `aggregate.configure { ... }`
        // from inside another container's configureEach — Gradle 8+ rejects
        // that nested-mutation pattern, which previously silently dropped the
        // kotlinNpmInstall dependency and left build/js/ unpopulated.
        dependsOn(
            subprojects.map { p ->
                p.tasks.matching { it.name.startsWith("collectSbom_") }
            }
        )
        // kotlinNpmInstall is registered by the Kotlin Gradle plugin only when
        // some subproject configures a js() target. matching{} silently yields
        // nothing if Kotlin/JS isn't applied anywhere.
        dependsOn(rootProject.tasks.matching { it.name == "kotlinNpmInstall" })

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

            // Workspace manifests — copy each Kalium JS workspace's package.json
            // into _workspace_manifests/. These declare the *runtime* dependencies
            // of non-test workspace packages; the notice generator BFS-walks them
            // to compute the runtime closure and filter out test/build-time
            // tooling (mocha, webpack, typescript and their transitives), which
            // would otherwise dominate the npm side of the customer notice.
            // Only the manifests are copied — not each workspace's full source
            // tree — since the BFS only needs the dependency declarations.
            val packagesRoot = rootProject.file("build/js/packages")
            if (packagesRoot.exists()) {
                val manifestsDir = java.io.File(outRoot, "npm/_workspace_manifests")
                packagesRoot.walkTopDown()
                    .onEnter { it.name != "node_modules" }
                    .filter { it.isFile && it.name == "package.json" }
                    .forEach { pj ->
                        val rel = pj.relativeTo(packagesRoot).path
                        val dest = java.io.File(manifestsDir, rel)
                        dest.parentFile.mkdirs()
                        pj.copyTo(dest, overwrite = true)
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
                    SBOM_TARGET_CONFIG_NAMES
                        .mapNotNull { sub.configurations.findByName(it) }
                        .filter { it.isCanBeResolved }
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

    registerFastCycloneDxTask()
}

/**
 * Generates the normal release SBOM directly from Gradle's resolved component graphs.
 *
 * Unlike [collectSbomArtifacts], this task never copies or extracts dependency archives. It uses
 * the official CycloneDX model/serializer and retains the legacy collector only for the optional
 * file-level ScanCode audit.
 */
private fun Project.registerFastCycloneDxTask() {
    val outputFile = layout.buildDirectory.file("sbom-fast/kalium.raw.cdx.json")
    val fragmentsDir = layout.buildDirectory.dir("sbom-fast/fragments")
    val expectedModuleIds = subprojects
        .filter(Project::isInSbomScope)
        .map { it.path.removePrefix(":").replace(':', '_') }
        .toSet()
    val expectedFragments = expectedModuleIds.map { moduleId ->
        layout.buildDirectory.file("sbom-fast/fragments/$moduleId.tsv")
    }

    tasks.register("generateFastSbom") {
        group = "sbom"
        description = "Generate a CycloneDX SBOM from resolved production dependency graphs."
        dependsOn(
            subprojects.map { subproject ->
                subproject.tasks.matching { it.name.startsWith("collectFastSbom_") }
            },
            tasks.matching { it.name == "kotlinNpmInstall" },
        )
        inputs.files(expectedFragments).withPropertyName("dependencyGraphFragments")
        outputs.file(outputFile)

        doLast {
            val rootRef = PackageURL(
                "maven",
                "com.wire",
                "kalium",
                project.version.toString(),
                null,
                null,
            ).toString()
            val components = sortedMapOf<String, CycloneDxComponent>()
            val edges = sortedMapOf<String, MutableSet<String>>()
            edges.getOrPut(rootRef) { sortedSetOf() }

            val stalePomDirs = layout.buildDirectory.dir("sbom-fast/poms").get().asFile
                .listFiles()
                .orEmpty()
                .filter { it.isDirectory && it.name !in expectedModuleIds }
            stalePomDirs.forEach(File::deleteRecursively)

            expectedFragments.map { it.get().asFile }.filter(File::isFile).sorted()
                .forEach { fragment ->
                fragment.forEachLine { line ->
                    val fields = line.split('\t')
                    when (fields.firstOrNull()) {
                        "C" -> {
                            require(fields.size == 6) { "Malformed component row in $fragment" }
                            components.getOrPut(fields[1]) {
                                CycloneDxComponent(fields[2], fields[3], fields[4])
                            }.configurations += fields[5]
                        }
                        "A" -> {
                            require(fields.size == 3) { "Malformed artifact row in $fragment" }
                            components[fields[1]]?.artifacts?.add(File(fields[2]))
                        }
                        "E" -> {
                            require(fields.size == 3) { "Malformed dependency row in $fragment" }
                            val parent = if (fields[1] == "ROOT") rootRef else fields[1]
                            edges.getOrPut(parent) { sortedSetOf() } += fields[2]
                        }
                    }
                }
            }

            val rootComponent = Component().apply {
                type = Component.Type.LIBRARY
                group = "com.wire"
                name = "kalium"
                version = project.version.toString()
                purl = rootRef
                bomRef = rootRef
            }
            val bom = Bom().apply {
                metadata = Metadata().apply { component = rootComponent }
                this.components = components.map { (ref, resolved) ->
                    Component().apply {
                        type = Component.Type.LIBRARY
                        group = resolved.group
                        name = resolved.name
                        version = resolved.version
                        purl = ref
                        bomRef = ref
                        scope = Component.Scope.REQUIRED
                        properties = listOf(
                            Property(
                                "wire:kalium:targets",
                                resolved.configurations.joinToString(","),
                            ),
                        )
                        hashes = resolved.artifacts
                            .filter(File::isFile)
                            .distinctBy(File::getAbsolutePath)
                            .map { artifact -> Hash(Hash.Algorithm.SHA_256, artifact.sha256()) }
                            .distinct()
                    }
                }
                dependencies = (listOf(rootRef) + components.keys).distinct().sorted().map { ref ->
                    Dependency(ref).apply {
                        edges[ref].orEmpty().forEach { child -> addDependency(Dependency(child)) }
                    }
                }
            }

            val output = outputFile.get().asFile
            output.parentFile.mkdirs()
            output.writeText(
                BomGeneratorFactory.createJson(Version.VERSION_16, bom)
                    .toJsonString(true),
            )
            val validationErrors = BomParserFactory.createParser(output)
                .validate(output, Version.VERSION_16)
            check(validationErrors.isEmpty()) {
                "Generated CycloneDX document failed schema validation: " +
                    validationErrors.joinToString { it.message.orEmpty() }
            }
            logger.lifecycle(
                "Wrote {} with {} external components",
                output.relativeTo(rootDir),
                components.size,
            )
        }
    }
}
