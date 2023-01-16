package com.wire.kalium.persistence.dao

import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant

data class ConnectionEntity(
    val conversationId: String,
    val from: String,
    val lastUpdateInstant: Instant,
    val qualifiedConversationId: ConversationIDEntity,
    val qualifiedToId: QualifiedIDEntity,
    val status: State,
    val toId: String,
    val shouldNotify: Boolean? = null,
    val otherUser: UserEntity? = null
) {

    @Deprecated("Dates should be stored using Instant. Use the primary constructor")
    constructor(
        conversationId: String,
        from: String,
        lastUpdate: String,
        qualifiedConversationId: ConversationIDEntity,
        qualifiedToId: QualifiedIDEntity,
        status: State,
        toId: String,
        shouldNotify: Boolean? = null,
        otherUser: UserEntity? = null
    ) : this(
        conversationId,
        from,
        lastUpdate.toInstant(),
        qualifiedConversationId,
        qualifiedToId,
        status,
        toId,
        shouldNotify,
        otherUser,
    )

    @Deprecated("Date formats are being standardised using Instant", ReplaceWith("lastUpdateInstant"))
    val lastUpdate: String get() = lastUpdateInstant.toIsoDateTimeString()

    enum class State {
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
}

interface ConnectionDAO {
    suspend fun getConnections(): Flow<List<ConnectionEntity>>
    suspend fun getConnectionRequests(): Flow<List<ConnectionEntity>>
    suspend fun insertConnection(connectionEntity: ConnectionEntity)
    suspend fun insertConnections(users: List<ConnectionEntity>)
    suspend fun updateConnectionLastUpdatedTime(lastUpdate: String, id: String)
    suspend fun deleteConnectionDataAndConversation(conversationId: QualifiedIDEntity)
    suspend fun getConnectionRequestsForNotification(): Flow<List<ConnectionEntity>>
    suspend fun updateNotificationFlag(flag: Boolean, userId: QualifiedIDEntity)
    suspend fun setAllConnectionsAsNotified()
}
