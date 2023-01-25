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

package com.wire.kalium.network.api.base.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
data class AccessTokenDTO(
    @SerialName("user") val userId: NonQualifiedUserId,
    @SerialName("access_token") val value: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("token_type") val tokenType: String
)

internal fun AccessTokenDTO.toSessionDto(refreshToken: String, qualifiedID: QualifiedID): SessionDTO = SessionDTO(
    userId = qualifiedID,
    tokenType = tokenType,
    accessToken = value,
    refreshToken = refreshToken
)

@JvmInline
value class RefreshTokenDTO(val value: String)
