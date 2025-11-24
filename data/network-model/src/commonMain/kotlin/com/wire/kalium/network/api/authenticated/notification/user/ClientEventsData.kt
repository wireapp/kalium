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

package com.wire.kalium.network.api.authenticated.notification.user

import com.wire.kalium.network.api.model.NonQualifiedUserId
import com.wire.kalium.network.api.model.SupportedProtocolDTO
import com.wire.kalium.network.api.model.UserAssetDTO
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoveClientEventData(
    @SerialName("id") val clientId: String
)

@Serializable
data class UserUpdateEventData(
    @SerialName("id") val nonQualifiedUserId: NonQualifiedUserId,
    @SerialName("accent_id") val accentId: Int?,
    @SerialName("name") val name: String?,
    @SerialName("handle") val handle: String?,
    @SerialName("email") val email: String?,
    @SerialName("sso_id_deleted") val ssoIdDeleted: Boolean?,
    @SerialName("assets") val assets: List<UserAssetDTO>?,
    @SerialName("supported_protocols")val supportedProtocols: List<SupportedProtocolDTO>?
)
