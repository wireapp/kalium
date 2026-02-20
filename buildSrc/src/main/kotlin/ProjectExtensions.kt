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

import org.codehaus.groovy.runtime.ProcessGroovyMethods
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.ivy
import org.gradle.kotlin.dsl.register
import java.util.Properties
import java.util.concurrent.TimeUnit

/**
 * Convenience method to obtain a property from `$projectRoot/local.properties` file
 * without passing the project param
 */
fun <T> Project.getLocalProperty(propertyName: String, defaultValue: T): T {
    return getLocalProperty(propertyName, defaultValue, this)
}

/**
 * Util to obtain property declared on `$projectRoot/local.properties` file or default
 */
@Suppress("UNCHECKED_CAST")
internal fun <T> getLocalProperty(propertyName: String, defaultValue: T, project: Project): T {
    val localProperties = Properties().apply {
        val localPropertiesFile = project.rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            load(localPropertiesFile.inputStream())
        }
    }

    val localValue = localProperties.getOrDefault(propertyName, defaultValue) as? T ?: defaultValue
    if (localValue != null) {
        println("> Reading local prop '$propertyName'")
    }
    return localValue
}

/**
 * Run command and return the [Process]
 */
fun String.execute(): Process = ProcessGroovyMethods.execute(this).also {
    it.waitFor(30, TimeUnit.SECONDS)
}

/**
 * Run command and return the output as text
 */
fun Process.text(): String = ProcessGroovyMethods.getText(this)

/**
 * Configure the repository for wire's detekt custom rules
 */
fun RepositoryHandler.wireDetektRulesRepo() {
    val repo = ivy("https://raw.githubusercontent.com/wireapp/wire-detekt-rules/main/dist") {
        patternLayout {
            artifact("/[module]-[revision].[ext]")
        }
        metadataSources.artifact()
    }
    exclusiveContent {
        forRepositories(repo)
        filter {
            includeModule("com.wire", "detekt-rules")
        }
    }
}

fun Project.registerCopyTestResourcesTask(target: String) {
    val targetName = target.replaceFirstChar { it.uppercase() }
    val task = tasks.register<Copy>("copy${targetName}TestResources") {
        from("src/commonTest/resources")
        into("build/bin/$target/debugTest/resources")
    }
    tasks.findByName("${target}Test")?.dependsOn(task)
}

private fun parseRequiredEnumOrFail(
    propertyName: String,
    source: String,
    rawValue: String?,
    allowedValues: Set<String>
): String? {
    if (rawValue == null) return null
    val normalizedValue = rawValue.trim().uppercase()
    return normalizedValue.takeIf { it in allowedValues } ?: error(
        "Invalid value '$rawValue' for Gradle property '$propertyName' from $source. " +
            "Expected one of: ${allowedValues.joinToString(", ")}."
    )
}

/**
 * Resolves a required enum-like Gradle property with strong validation and consumer-first semantics.
 *
 * Resolution order:
 * 1) CLI project property (`-P<propertyName>=...`)
 * 2) If this is an included build, consumer root project properties
 * 3) If this is a standalone build, local/root `gradle.properties`
 *
 * No default is applied. A descriptive failure is raised when missing or invalid.
 */
fun Project.resolveRequiredEnumGradleProperty(
    propertyName: String,
    allowedValues: Set<String>,
    purpose: String
): String {
    val valuesForExample = allowedValues.joinToString("|")
    val valuesForDisplay = allowedValues.joinToString(", ")
    val fromCli = parseRequiredEnumOrFail(
        propertyName = propertyName,
        source = "command line (-P$propertyName=...)",
        rawValue = gradle.startParameter.projectProperties[propertyName],
        allowedValues = allowedValues
    )
    if (fromCli != null) return fromCli

    val fromParent = parseRequiredEnumOrFail(
        propertyName = propertyName,
        source = "consumer root gradle.properties",
        rawValue = gradle.parent
            ?.rootProject
            ?.properties
            ?.get(propertyName)
            ?.toString(),
        allowedValues = allowedValues
    )
    if (fromParent != null) return fromParent

    if (gradle.parent != null) {
        error(
            "Missing required Gradle property '$propertyName'. " +
                "Kalium intentionally has no default for this flag because $purpose and that behavior " +
                "must be explicitly chosen by the consumer build. " +
                "Set '$propertyName=$valuesForExample' in the consumer root gradle.properties, " +
                "or pass -P$propertyName=$valuesForExample. " +
                "Allowed values: $valuesForDisplay."
        )
    }

    val fromLocal = parseRequiredEnumOrFail(
        propertyName = propertyName,
        source = "local gradle.properties",
        rawValue = providers
            .gradleProperty(propertyName)
            .orNull,
        allowedValues = allowedValues
    )
    if (fromLocal != null) return fromLocal

    error(
        "Missing required Gradle property '$propertyName'. " +
            "Kalium intentionally has no default for this flag because $purpose. " +
            "Set '$propertyName=$valuesForExample' in gradle.properties, " +
            "or pass -P$propertyName=$valuesForExample. " +
            "Allowed values: $valuesForDisplay."
    )
}
