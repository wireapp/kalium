/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

import org.gradle.api.artifacts.ProjectDependency

plugins {
    id(libs.plugins.kalium.library.get().pluginId)
    alias(libs.plugins.ksp)
    alias(libs.plugins.mokkery)
}

kaliumLibrary {
    multiplatform {
        enableJs.set(false)
    }
}

kotlin {
    sourceSets.configureEach {
        languageSettings.optIn("com.wire.kalium.util.InternalKaliumApi")
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.core.common)
                api(projects.core.cryptography)
                api(projects.core.data)
                implementation(projects.core.logger)
                api(projects.core.util)
                implementation(projects.data.dataMappers)
                implementation(projects.data.network)
                api(projects.data.persistence)
                api(projects.domain.cells)
                api(projects.domain.eventProcessing)
                api(projects.domain.messaging.shared)
                api(projects.domain.messaging.hooks)
                api(libs.coroutines.core)
                api(libs.ktxDateTime)
                implementation(libs.concurrentCollections)
                implementation(libs.ktxSerialization)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(projects.data.persistenceTest)
                implementation(projects.data.protobuf)
                implementation(projects.test.dataMocks)
                implementation(libs.coroutines.test)
                implementation(libs.okio.core)
                implementation(libs.turbine)
            }
        }
    }
}

// Keep receiver implementations reusable by both :logic and a future lightweight NSE facade.
configurations.configureEach {
    dependencies.withType<ProjectDependency>().configureEach {
        require(path != ":logic") {
            ":domain:messaging:receiving must not depend on :logic"
        }
    }
}
