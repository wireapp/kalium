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
                api(projects.core.cryptography)
                api(projects.core.data)
                api(projects.data.networkModel)
                api(libs.ktxDateTime)
                implementation(projects.core.logger)
                api(projects.core.util)
                implementation(libs.coroutines.core)
                implementation(libs.ktxSerialization)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.coroutines.test)
            }
        }
    }
}

// This module is the lower-level event-processing boundary. A dependency on :logic would make the
// NSE pull in the application session and observer graph and would introduce a dependency cycle.
configurations.configureEach {
    dependencies.withType<ProjectDependency>().configureEach {
        require(path != ":logic") {
            ":domain:event-processing must not depend on :logic"
        }
    }
}
