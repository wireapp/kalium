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

package com.wire.kalium.network.api.base.authenticated.notification.team

import com.wire.kalium.network.api.base.model.NonQualifiedUserId
import com.wire.kalium.network.api.base.authenticated.TeamsApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TeamUpdateData(
    @SerialName("icon") val icon: String,
    @SerialName("name") val name: String,
)

@Serializable
data class TeamMemberIdData(
    @SerialName("user") val nonQualifiedUserId: NonQualifiedUserId,
)

@Serializable
data class PermissionsData(
    @SerialName("permissions") val permissions: TeamsApi.Permissions,
    @SerialName("user") val nonQualifiedUserId: NonQualifiedUserId
)
