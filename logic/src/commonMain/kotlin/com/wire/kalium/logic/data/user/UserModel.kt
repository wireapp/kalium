package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.VALUE_DOMAIN_SEPARATOR
import com.wire.kalium.logic.data.publicuser.model.OtherUser

typealias UserId = QualifiedID

abstract class User {
    abstract val id: UserId
}

//TODO we should extract ConnectionModel and ConnectionState to separate logic AR-1734
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
    val name: String?,
    val handle: String?,
    val email: String?,
    val phone: String?,
    val accentId: Int,
    val team: String?,
    val connectionStatus: ConnectionState,
    val previewPicture: UserAssetId?,
    val completePicture: UserAssetId?,
    val availabilityStatus: UserAvailabilityStatus?
) : User()

typealias UserAssetId = String

fun String.toUserId(): UserId {
    if (contains(VALUE_DOMAIN_SEPARATOR)) {
        split(VALUE_DOMAIN_SEPARATOR).also {
            return UserId(value = it.first(), domain = it.last())
        }
    } else return UserId(value = this, domain = "")
}
