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

typealias ConversationId = QualifiedID
typealias NonQualifiedConversationId = String
typealias UserId = QualifiedID
typealias NonQualifiedUserId = String
typealias TeamId = String
typealias AssetId = QualifiedID
typealias AssetKey = String
typealias MLSPublicKey = String

@Serializable
data class QualifiedID(
    @SerialName("id")
    val value: String,
    @SerialName("domain")
    val domain: String
)

@Serializable
data class UserSsoIdDTO(
    @SerialName("scim_external_id")
    val scimExternalId: String?,
    @SerialName("subject")
    val subject: String?,
    @SerialName("tenant")
    val tenant: String?
)
