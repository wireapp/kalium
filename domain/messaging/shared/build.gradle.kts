/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
                implementation(projects.core.cryptography)
                api(projects.core.data)
                api(projects.core.util)
                implementation(projects.data.dataMappers)
                api(projects.data.persistence)
                api(libs.ktxDateTime)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(projects.data.networkModel)
                implementation(libs.coroutines.test)
            }
        }
    }
}

// This module is shared by messaging senders and receivers and must remain below their orchestration layers.
configurations.configureEach {
    dependencies.withType<ProjectDependency>().configureEach {
        require(path !in setOf(":logic", ":domain:messaging:receiving", ":domain:messaging:sending")) {
            ":domain:messaging:shared must not depend on :logic, receiving, or sending"
        }
    }
}
