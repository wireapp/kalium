package com.wire.kalium.persistence.dao.reaction

import com.wire.kalium.persistence.ReactionsQueries
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity

interface ReactionDAO {

    suspend fun updateReactions(
        originalMessageId: String,
        conversationId: ConversationIDEntity,
        senderUserId: UserIDEntity,
        date: String,
        reactions: UserReactionsEntity
    )

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
        senderUserId: UserIDEntity,
        emoji: String
    )

    suspend fun getReaction(
        originalMessageId: String,
        conversationId: ConversationIDEntity,
        senderUserId: UserIDEntity
    ): UserReactionsEntity
}

class ReactionDAOImpl(private val reactionsQueries: ReactionsQueries) : ReactionDAO {

    override suspend fun updateReactions(
        originalMessageId: String,
        conversationId: ConversationIDEntity,
        senderUserId: UserIDEntity,
        date: String,
        reactions: UserReactionsEntity
    ) {
        reactionsQueries.transaction {
            reactionsQueries.deleteAllReactionsOnMessageFromUser(originalMessageId, conversationId, senderUserId)
            reactions.forEach {
                reactionsQueries.insertReaction(originalMessageId, conversationId, senderUserId, it, date)
            }
        }
    }

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

    override suspend fun deleteReaction(
        originalMessageId: String,
        conversationId: ConversationIDEntity,
        senderUserId: UserIDEntity,
        emoji: String
    ) {
        reactionsQueries.deleteReaction(originalMessageId, conversationId, senderUserId, emoji)
    }

    override suspend fun getReaction(
        originalMessageId: String,
        conversationId: ConversationIDEntity,
        senderUserId: UserIDEntity
    ): UserReactionsEntity = reactionsQueries
        .selectByMessageIdAndConversationIdAndSenderId(originalMessageId, conversationId, senderUserId) { _, _, _, emoji, _ ->
            emoji
        }
        .executeAsList()
        .toSet()
}
