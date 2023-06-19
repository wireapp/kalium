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

package com.wire.kalium.logic.data.client

import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.logic.data.conversation.ClientId
import kotlinx.datetime.Instant

data class RegisterClientParam(
    val password: String?,
    val preKeys: List<PreKeyCrypto>,
    val lastKey: PreKeyCrypto,
    val deviceType: DeviceType?,
    val label: String?,
    val capabilities: List<ClientCapability>?,
    val clientType: ClientType?,
    val model: String?,
    val cookieLabel: String?,
    val secondFactorVerificationCode: String? = null,
)

data class DeleteClientParam(
    val password: String?,
    val clientId: ClientId
)

data class Client(
    val id: ClientId,
    val type: ClientType?,
    /**
     * Time when client was registered / created.
     */
    val registrationTime: Instant?,
    /**
     * last time the client was active.
     *
     * Note that the timestamp has reduced precision, it's only safe
     * to operate on the precision of weeks.
     * */
    val lastActive: Instant?,
    val isVerified: Boolean,
    val isValid: Boolean,
    val deviceType: DeviceType?,
    val label: String?,
    val model: String?,
    val mlsPublicKeys: Map<String, String>?,
    val isMLSCapable: Boolean
)

enum class ClientType {
    Temporary,
    Permanent,
    LegalHold;
}

enum class DeviceType {
    Phone,
    Tablet,
    Desktop,
    LegalHold,
    Unknown;
}

enum class ClientCapability {
    LegalHoldImplicitConsent;
}

data class OtherUserClient(
    val deviceType: DeviceType,
    val id: String,
    val isValid: Boolean,
    val isVerified: Boolean
)
