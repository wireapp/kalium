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

import io.kayan.gradle.ExperimentalKayanGenerationApi

plugins {
    id(libs.plugins.kalium.library.get().pluginId)
    alias(libs.plugins.kayan)
}

@OptIn(ExperimentalKayanGenerationApi::class)
kayan {
    inheritFromRoot()
    packageName.set("com.wire.kalium.usernetwork.di")
    className.set("UserNetworkBuildConfig")
    targets {
        android()
        jvm()
        sourceSet(sourceSetName = "appleMain", targetName = "apple")
    }
    schema {
        include("provider_cache_scope")
    }
}

kaliumLibrary {
    multiplatform {
        enableJs.set(false)
    }
}

kotlin {
    explicitApi()
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.data.network)
                implementation(projects.core.util)
                implementation(libs.concurrentCollections)
                implementation(libs.statelyCommons)
            }
        }
    }
}
