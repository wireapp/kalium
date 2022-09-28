package com.wire.kalium.logic.data.user

import com.wire.kalium.logger.obfuscateDomain
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.user.type.UserType
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
        return "Connection( conversationId: ${conversationId.obfuscateId()}, from:${from.obfuscateId()}," +
                " lastUpdate:$lastUpdate," +
                " qualifiedConversationId:${qualifiedConversationId.value.obfuscateId()}" +
                "@${qualifiedConversationId.domain.obfuscateDomain()}, " +
                "qualifiedToId:${qualifiedToId.value.obfuscateId()}@${qualifiedToId.domain.obfuscateDomain()}, " +
                "status:$status, toId:${toId.obfuscateId()} " +
                "fromUser:${fromUser?.id?.value?.obfuscateId()}@ ${fromUser?.id?.domain?.obfuscateDomain()} "
    }
}

enum class UserAvailabilityStatus {
    NONE, AVAILABLE, BUSY, AWAY
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
    override val availabilityStatus: UserAvailabilityStatus
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
    val userType: UserType,
    override val availabilityStatus: UserAvailabilityStatus,
    val botService: BotService?,
    val deleted: Boolean
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
)

typealias UserAssetId = AssetId
typealias AssetId = QualifiedID
