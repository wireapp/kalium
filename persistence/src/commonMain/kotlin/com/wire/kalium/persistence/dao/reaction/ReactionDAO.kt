package com.wire.kalium.persistence.dao.reaction

import com.wire.kalium.persistence.ReactionsQueries
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity

interface ReactionDAO {
    suspend fun insertReaction(
        originalMessageId: String,
        conversationId: ConversationIDEntity,
        senderUserId: UserIDEntity,
        date: String,
        emoji: String
    )
    suspend fun deleteReaction(
        originalMessageId: String,
        conversationId: ConversationIDEntity,
        senderUserId: UserIDEntity
    )
}

class ReactionDAOImpl(private val reactionsQueries: ReactionsQueries) : ReactionDAO {
    override suspend fun insertReaction(
        originalMessageId: String,
        conversationId: ConversationIDEntity,
        senderUserId: UserIDEntity,
        date: String,
        emoji: String
    ) {
        reactionsQueries.insertReaction(
            originalMessageId, conversationId, senderUserId, emoji, date
        )
    }

    override suspend fun deleteReaction(originalMessageId: String, conversationId: ConversationIDEntity, senderUserId: UserIDEntity) {
        reactionsQueries.deleteReaction(originalMessageId, conversationId, senderUserId)
    }
}
