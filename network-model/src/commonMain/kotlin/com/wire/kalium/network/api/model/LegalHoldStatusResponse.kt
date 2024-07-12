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

package com.wire.kalium.network.api.model

import com.wire.kalium.network.api.authenticated.client.ClientIdDTO
import com.wire.kalium.network.api.authenticated.keypackage.LastPreKeyDTO
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LegalHoldStatusDTO {
    @SerialName("enabled")
    ENABLED,
    @SerialName("pending")
    PENDING,
    @SerialName("disabled")
    DISABLED,
    @SerialName("no_consent")
    NO_CONSENT
}

@Serializable
data class LegalHoldStatusResponse(
    @SerialName("status") val legalHoldStatusDTO: LegalHoldStatusDTO,
    @SerialName("client") val clientId: ClientIdDTO?,
    @SerialName("last_prekey") val lastPreKey: LastPreKeyDTO?,
)
