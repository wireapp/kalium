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
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension

private const val CENTRAL_STAGING_REPOSITORY_NAME = "mavenCentralStaging"
private const val CENTRAL_SNAPSHOTS_REPOSITORY_NAME = "mavenCentralSnapshots"
private const val CENTRAL_SNAPSHOTS_REPOSITORY_URL = "https://central.sonatype.com/repository/maven-snapshots/"
private const val SIGNING_REQUIRED_PROPERTY = "kalium.mavenCentral.signingRequired"
private const val PUBLISH_VERSION_PROPERTY = "kalium.publish.version"
private const val PUBLISH_VERSION_ENV = "KALIUM_PUBLISH_VERSION"
private const val MAVEN_CENTRAL_USERNAME_PROPERTY = "mavenCentralUsername"
private const val MAVEN_CENTRAL_PASSWORD_PROPERTY = "mavenCentralPassword"
private const val SIGNING_KEY_ID_PROPERTY = "signingInMemoryKeyId"
private const val SIGNING_KEY_PROPERTY = "signingInMemoryKey"
private const val SIGNING_PASSWORD_PROPERTY = "signingInMemoryKeyPassword"

private val excludedMavenCentralProjectPathPrefixes = setOf(
    ":sample",
    ":test",
    ":tools"
)

private val excludedMavenCentralProjectPaths = setOf(
    ":data:persistence-test",
    ":domain:calling-notifications",
    ":domain:conversation-history",
    ":domain:messaging:receiving"
)

internal fun Project.configureKaliumMavenPublishingIfNeeded() {
    if (!isPublishedToMavenCentral()) return

    pluginManager.apply("base")
    pluginManager.apply("maven-publish")
    pluginManager.apply("signing")

    providers.gradleProperty(PUBLISH_VERSION_PROPERTY)
        .orElse(providers.environmentVariable(PUBLISH_VERSION_ENV))
        .orNull
        ?.let { version = it }

    val baseArtifactId = mavenCentralArtifactId()
    extensions.configure<BasePluginExtension> {
        archivesName.set(baseArtifactId)
    }

    val signingKeyId = providers.gradleProperty(SIGNING_KEY_ID_PROPERTY)
    val signingKey = providers.gradleProperty(SIGNING_KEY_PROPERTY)
    val signingPassword = providers.gradleProperty(SIGNING_PASSWORD_PROPERTY)
    val signingRequired = providers.gradleProperty(SIGNING_REQUIRED_PROPERTY)
        .map(String::toBoolean)
        .orElse(false)
    val mavenCentralUsername = providers.gradleProperty(MAVEN_CENTRAL_USERNAME_PROPERTY)
    val mavenCentralPassword = providers.gradleProperty(MAVEN_CENTRAL_PASSWORD_PROPERTY)

    afterEvaluate {
        providers.gradleProperty(PUBLISH_VERSION_PROPERTY)
            .orElse(providers.environmentVariable(PUBLISH_VERSION_ENV))
            .orNull
            ?.let { version = it }

        extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    name = CENTRAL_STAGING_REPOSITORY_NAME
                    url = rootProject.layout.buildDirectory.dir("maven-central-staging").get().asFile.toURI()
                }
                maven {
                    name = CENTRAL_SNAPSHOTS_REPOSITORY_NAME
                    url = uri(CENTRAL_SNAPSHOTS_REPOSITORY_URL)
                    credentials(PasswordCredentials::class.java) {
                        username = mavenCentralUsername.orNull
                        password = mavenCentralPassword.orNull
                    }
                }
            }

            publications.withType<MavenPublication>().configureEach {
                artifactId = publicationArtifactId(baseArtifactId)
                val publicationJavadocJar = tasks.register<Jar>("mavenCentral${name.taskNameSuffix()}JavadocJar") {
                    archiveBaseName.set(artifactId)
                    archiveClassifier.set("javadoc")
                    from(rootProject.file("README.md")) {
                        into("META-INF")
                    }
                }
                artifact(publicationJavadocJar)
                pom {
                    name.set("Kalium ${project.path.removePrefix(":").replace(':', ' ')}")
                    description.set("Kotlin Multiplatform messaging SDK for the Wire messaging platform.")
                    url.set("https://github.com/wireapp/kalium")
                    licenses {
                        license {
                            name.set("GNU General Public License, Version 3.0")
                            url.set("https://www.gnu.org/licenses/gpl-3.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("wire")
                            name.set("Wire")
                            organization.set("Wire Swiss GmbH")
                            organizationUrl.set("https://wire.com")
                        }
                    }
                    scm {
                        connection.set("scm:git:https://github.com/wireapp/kalium.git")
                        developerConnection.set("scm:git:ssh://git@github.com/wireapp/kalium.git")
                        url.set("https://github.com/wireapp/kalium")
                    }
                }
            }
        }

        val hasAnySigningCredential = listOf(signingKeyId, signingKey, signingPassword).any { it.isPresent }
        val hasAllSigningCredentials = listOf(signingKeyId, signingKey, signingPassword).all { it.isPresent }
        if ((signingRequired.get() || hasAnySigningCredential) && !hasAllSigningCredentials) {
            error(
                "Maven Central signing requires $SIGNING_KEY_ID_PROPERTY, " +
                        "$SIGNING_KEY_PROPERTY, and $SIGNING_PASSWORD_PROPERTY."
            )
        }

        val publishing = extensions.getByType<PublishingExtension>()
        extensions.configure<SigningExtension> {
            isRequired = signingRequired.get()
            if (hasAllSigningCredentials) {
                useInMemoryPgpKeys(signingKeyId.get(), signingKey.get(), signingPassword.get())
                sign(publishing.publications)
            }
        }
    }

    tasks.withType<Sign>().configureEach {
        onlyIf {
            signingRequired.get() || listOf(signingKeyId, signingKey, signingPassword).all { it.isPresent }
        }
    }
}

private fun Project.isPublishedToMavenCentral(): Boolean =
    path !in excludedMavenCentralProjectPaths &&
            excludedMavenCentralProjectPathPrefixes.none { excludedPrefix ->
                path == excludedPrefix || path.startsWith("$excludedPrefix:")
            }

private fun Project.mavenCentralArtifactId(): String =
    path
        .split(':')
        .filter { it.isNotBlank() }
        .joinToString(separator = "-") { it.replace('_', '-') }

private fun MavenPublication.publicationArtifactId(baseArtifactId: String): String =
    if (name == "kotlinMultiplatform") {
        baseArtifactId
    } else {
        "$baseArtifactId-${name.lowercase()}"
    }

private fun String.taskNameSuffix(): String =
    replaceFirstChar { firstChar ->
        if (firstChar.isLowerCase()) {
            firstChar.titlecase()
        } else {
            firstChar.toString()
        }
    }
