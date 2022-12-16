package com.wire.kalium.plugins

import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.provider.Provider

val Project.libs: VersionCatalog get() {
    val catalogs = extensions.getByType(VersionCatalogsExtension::class.java)
    return catalogs.named("libs")
}

fun Project.library(name: String): Provider<MinimalExternalModuleDependency> {
    return libs.findLibrary(name).get()
}
