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

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    `maven-publish`
}

group = "com.wire.kalium"
version = providers.gradleProperty("kalium.appleAvsRuntimePlugin.version")
    .orElse(providers.gradleProperty("kalium.publish.version"))
    .getOrElse("0.1.0-SNAPSHOT")
description = "Links and embeds the dynamic Apple AVS XCFramework for Kalium consumers"

repositories {
    google()
    mavenCentral()
}

val kaliumCatalog = extensions.getByType<VersionCatalogsExtension>().named("kalium")
val kotlinVersion = kaliumCatalog.findVersion("kotlin").get().requiredVersion
val avsVersion = kaliumCatalog.findVersion("avs").get().requiredVersion
// Provenance of `archiveSha256`: it is the sha256 GitHub publishes for the official
// `avs.xcframework.zip` release asset, readable without downloading it:
//
//   curl -sS https://api.github.com/repos/wireapp/wire-avs/releases/tags/<avsVersion> \
//     | jq -r '.assets[] | select(.name == "avs.xcframework.zip") | .digest'
//
// When bumping `avs` in gradle/libs.versions.toml, take the digest from that field (or from the
// wire-avs release job output) and add a new entry below. Do NOT download the archive locally and
// paste back whatever it hashes to: that makes any served bytes self-certifying and defeats the
// pin. `macosMinimumVersion` comes from the macOS slice's LC_BUILD_VERSION minos.
//
// Limitation: this is trust-on-first-use. A digest published by a compromised release process
// would match a compromised archive. Defending against that needs a signed attestation or a
// reproducible build of AVS, not another checksum published alongside the asset.
val avsRuntimeMetadata = requireNotNull(
    mapOf(
        "10.4.32" to mapOf(
            "archiveSha256" to "8692b5ce021fe577d40f722c465d53f91700a07b2f5fc373857878cfd6a15a45",
            "macosMinimumVersion" to "15.0",
        ),
        "10.4.34" to mapOf(
            "archiveSha256" to "e86d87c619f86d0941a8362cc00eb0127646b9509534676f7e73b6b9b1a0f50e",
            "macosMinimumVersion" to "15.0",
        ),
        "10.5.3" to mapOf(
            "archiveSha256" to "5f3e47408c31666c65bac2811ef7a26a353e551972a5f138bd774b7daec01d82",
            "macosMinimumVersion" to "15.0",
        ),
    )[avsVersion]
) {
    "Apple AVS runtime metadata is missing for AVS $avsVersion. " +
        "Either select a registered AVS version in gradle/libs.versions.toml or add an " +
        "avsRuntimeMetadata entry for $avsVersion with the official archiveSha256 and " +
        "macosMinimumVersion."
}
val avsArchiveSha256 = avsRuntimeMetadata.getValue("archiveSha256")
val avsMacosMinimumVersion = avsRuntimeMetadata.getValue("macosMinimumVersion")

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test-junit5"))
}

gradlePlugin {
    plugins {
        create("appleAvsRuntime") {
            id = "com.wire.kalium.apple-avs-runtime"
            implementationClass = "com.wire.kalium.gradle.avs.AppleAvsRuntimePlugin"
            displayName = "Kalium Apple AVS runtime"
            description = project.description
        }
    }
}

tasks.processResources {
    inputs.property("avsVersion", avsVersion)
    inputs.property("avsArchiveSha256", avsArchiveSha256)
    inputs.property("avsMacosMinimumVersion", avsMacosMinimumVersion)
    filesMatching("com/wire/kalium/gradle/avs/avs-runtime.properties") {
        expand(
            "avsVersion" to avsVersion,
            "avsArchiveSha256" to avsArchiveSha256,
            "avsMacosMinimumVersion" to avsMacosMinimumVersion,
        )
    }
}

tasks.test {
    useJUnitPlatform()
    val kaliumVersionCatalog = rootProject.file("../../gradle/libs.versions.toml")
    inputs.file(kaliumVersionCatalog)
    systemProperty("kalium.versionCatalog", kaliumVersionCatalog.absolutePath)
}
