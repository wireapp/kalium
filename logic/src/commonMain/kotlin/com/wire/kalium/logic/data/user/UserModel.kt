package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.VALUE_DOMAIN_SEPARATOR
import com.wire.kalium.logic.data.user.type.UserType

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
)

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
    val availabilityStatus: UserAvailabilityStatus
) : User()

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
    val availabilityStatus: UserAvailabilityStatus
) : User()

typealias UserAssetId = AssetId
typealias AssetId = QualifiedID

fun String.toUserId(): UserId {
    if (contains(VALUE_DOMAIN_SEPARATOR)) {
        split(VALUE_DOMAIN_SEPARATOR).also {
            return UserId(value = it.first(), domain = it.last())
        }
    } else return UserId(value = this, domain = "")
}
