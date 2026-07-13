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

plugins {
    id(libs.plugins.sqldelight.get().pluginId)
    id(libs.plugins.kalium.library.get().pluginId)
}

kaliumLibrary {
    multiplatform {
        enableJs.set(false)
    }
}

sqldelight {
    databases {
        create("NotificationInboxDatabase") {
            dialect(libs.sqldelight.dialect.get().toString())
            packageName.set("com.wire.kalium.notificationinbox.db")
            generateAsync.set(true)
            srcDirs.setFrom("src/commonMain/db")
            schemaOutputDirectory.set(file("src/commonMain/db/schemas"))
        }
    }
}

kotlin {
    explicitApi()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.coroutines.core)
                implementation(libs.sqldelight.async)
                implementation(libs.sqldelight.runtime)
            }
        }
        val appleMain by getting {
            dependencies {
                implementation(libs.sqldelight.nativeDriver)
            }
        }
    }
}
