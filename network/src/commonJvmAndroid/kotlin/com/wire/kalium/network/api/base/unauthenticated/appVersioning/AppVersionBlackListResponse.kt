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

package com.wire.kalium.network.api.base.unauthenticated.appVersioning

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
actual data class AppVersionBlackListResponse(
    @SerialName("oldestAccepted") val oldestAccepted: Int,
    @SerialName("blacklisted") val blacklisted: List<Int>
) {
    actual fun isAppNeedsToBeUpdated(currentAppVersion: Int): Boolean =
        currentAppVersion < oldestAccepted || blacklisted.contains(currentAppVersion)
}

actual fun appVersioningUrlPlatformPath(): String = "android"
