package com.wire.kalium.persistence.dao

import kotlinx.coroutines.flow.Flow

data class ConnectionEntity (
    val conversationId: String,
    val from: String,
    val lastUpdate: String,
    val qualifiedConversationId: ConversationIDEntity,
    val qualifiedToId: QualifiedIDEntity,
    val status: State,
    val toId: String
) {

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
    suspend fun getConnectionRequests(): Flow<List<ConnectionEntity>>
    suspend fun insertConnection(connectionEntity: ConnectionEntity)
}
