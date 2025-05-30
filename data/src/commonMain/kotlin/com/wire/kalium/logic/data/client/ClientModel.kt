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

package com.wire.kalium.logic.data.client

import com.wire.kalium.logic.data.conversation.ClientId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

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
    val isMLSCapable: Boolean,
    val isAsyncNotificationsCapable: Boolean
) {
    companion object {
        val INACTIVE_DURATION = 28.days
    }
}

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

sealed class ClientCapability {
    data object LegalHoldImplicitConsent : ClientCapability()
    data object ConsumableNotifications : ClientCapability()
    data class Unknown(val name: String) : ClientCapability()
}

data class OtherUserClient(
    val deviceType: DeviceType,
    val id: String,
    val isValid: Boolean,
    val isProteusVerified: Boolean
)

data class UpdateClientCapabilitiesParam(
    val capabilities: List<ClientCapability>
)
/**
 * True if the client is considered to be in active use.
 *
 * A client is considered active if it has connected to the backend within
 * the `INACTIVE_DURATION`.
 */
val Client.isActive: Boolean
    get() = (lastActive ?: registrationTime)?.let { (Clock.System.now() - it) < Client.INACTIVE_DURATION } ?: false
