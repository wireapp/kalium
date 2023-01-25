/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.network.api.base.authenticated.userDetails

import com.wire.kalium.network.api.base.model.NonQualifiedUserId
import com.wire.kalium.network.api.base.model.TeamId
import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.network.api.base.model.LegalHoldStatusResponse
import com.wire.kalium.network.api.base.model.ServiceDTO
import com.wire.kalium.network.api.base.model.UserAssetDTO
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfileDTO(
    @SerialName("qualified_id") val id: UserId,
    @SerialName("name") val name: String,
    @SerialName("handle") val handle: String?,
    @SerialName("legalhold_status") val legalHoldStatus: LegalHoldStatusResponse,
    @SerialName("team") val teamId: TeamId?,
    @SerialName("accent_id") val accentId: Int,
    @SerialName("assets") val assets: List<UserAssetDTO>,
    @SerialName("deleted") val deleted: Boolean?,
    @SerialName("email") val email: String?,
    @SerialName("expires_at") val expiresAt: String?,
    @Deprecated("use id instead", replaceWith = ReplaceWith("this.id"))
    @SerialName("id") val nonQualifiedId: NonQualifiedUserId,
    @SerialName("service") val service: ServiceDTO?
)
