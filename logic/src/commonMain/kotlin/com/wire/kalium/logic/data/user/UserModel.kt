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

package com.wire.kalium.logic.data.user

import com.wire.kalium.logger.obfuscateDomain
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.VALUE_DOMAIN_SEPARATOR
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.util.serialization.toJsonElement
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias UserId = QualifiedID

sealed class User {
    abstract val id: UserId
    abstract val name: String?
    abstract val handle: String?
    abstract val email: String?
    abstract val phone: String?
    abstract val accentId: Int
    abstract val teamId: TeamId?
    abstract val previewPicture: UserAssetId?
    abstract val completePicture: UserAssetId?
    abstract val availabilityStatus: UserAvailabilityStatus
    abstract val expiresAt: Instant?
    abstract val supportedProtocols: Set<SupportedProtocol>?
    abstract val userType: UserType
}

// TODO we should extract ConnectionModel and ConnectionState to separate logic AR-1734
data class Connection(
    val conversationId: String,
    val from: String,
    val lastUpdate: String,
    val qualifiedConversationId: ConversationId,
    val qualifiedToId: UserId,
    val status: ConnectionState,
    val toId: String,
    val fromUser: OtherUser? = null
) {
    override fun toString(): String {
        return "${this.toMap().toJsonElement()}"
    }

    fun toMap(): Map<String, String> {
        val qId = qualifiedConversationId
        return mapOf(
            "conversationId" to conversationId.obfuscateId(),
            "from" to from.obfuscateId(),
            "lastUpdate" to lastUpdate,
            "qualifiedConversationId" to "${qId.value.obfuscateId()}@${qId.domain.obfuscateDomain()}",
            "qualifiedToId" to "${qualifiedToId.value.obfuscateId()}@${qualifiedToId.domain.obfuscateDomain()}",
            "status" to status.name,
            "toId" to toId.obfuscateId(),
            "fromUser" to "${fromUser?.id?.value?.obfuscateId() ?: "null"}@${fromUser?.id?.domain?.obfuscateDomain() ?: "null"}"
        )
    }
}

enum class UserAvailabilityStatus {
    NONE, AVAILABLE, BUSY, AWAY
}

enum class SupportedProtocol {
    PROTEUS, MLS
}

enum class ConnectionState {
    /** Default - No connection state */
    NOT_CONNECTED,

    /** The other user has sent a connection request to this one */
    PENDING,

    /** This user has sent a connection request to another user */
    SENT,

    /** The user has been blocked */
    BLOCKED,

    /** The connection has been ignored */
    IGNORED,

    /** The connection has been cancelled */
    CANCELLED,

    /** The connection is missing legal hold consent */
    MISSING_LEGALHOLD_CONSENT,

    /** The connection is complete and the conversation is in its normal state */
    ACCEPTED
}

@Serializable
data class SsoId(
    @SerialName("scim_external_id") val scimExternalId: String?,
    @SerialName("subject") val subject: String?,
    @SerialName("tenant") val tenant: String?
)

data class SelfUser(
    override val id: UserId,
    override val name: String?,
    override val handle: String?,
    override val email: String?,
    override val phone: String?,
    override val accentId: Int,
    override val teamId: TeamId?,
    // TODO: why does self user needs a ConnectionState
    val connectionStatus: ConnectionState,
    override val previewPicture: UserAssetId?,
    override val completePicture: UserAssetId?,
    override val userType: UserType,
    override val availabilityStatus: UserAvailabilityStatus,
    override val expiresAt: Instant? = null,
    override val supportedProtocols: Set<SupportedProtocol>?
) : User()

data class OtherUserMinimized(
    val id: UserId,
    val name: String?,
    val completePicture: UserAssetId?,
    val userType: UserType,
)

data class OtherUser(
    override val id: UserId,
    override val name: String?,
    override val handle: String?,
    override val email: String? = null,
    override val phone: String? = null,
    override val accentId: Int,
    override val teamId: TeamId?,
    val connectionStatus: ConnectionState = ConnectionState.NOT_CONNECTED,
    override val previewPicture: UserAssetId?,
    override val completePicture: UserAssetId?,
    override val userType: UserType,
    override val availabilityStatus: UserAvailabilityStatus,
    override val supportedProtocols: Set<SupportedProtocol>?,
    val botService: BotService?,
    val deleted: Boolean,
    val defederated: Boolean,
    override val expiresAt: Instant? = null,
    val isProteusVerified: Boolean,
    val activeOneOnOneConversationId: ConversationId? = null
) : User() {

    /**
     * convenience computed field to obtain the unavailable users
     */
    val isUnavailableUser
        get() = !deleted && name.orEmpty().isEmpty()
}

data class BotService(
    val id: String,
    val provider: String
) {
    override fun toString(): String {
        return "$id$VALUE_DOMAIN_SEPARATOR$provider"
    }
}

typealias UserAssetId = AssetId
typealias AssetId = QualifiedID
