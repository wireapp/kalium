package com.wire.kalium.persistence.dao.reaction

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.ReactionsQueries
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.util.mapToList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

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

    suspend fun observeMessageReactions(
        conversationId: QualifiedIDEntity,
        messageId: String
    ): Flow<List<MessageReactionEntity>>
}

class ReactionDAOImpl(
    private val reactionsQueries: ReactionsQueries,
    private val queriesContext: CoroutineContext
) : ReactionDAO {

    override suspend fun updateReactions(
        originalMessageId: String,
        conversationId: ConversationIDEntity,
        senderUserId: UserIDEntity,
        date: String,
        reactions: UserReactionsEntity
    ) = withContext(queriesContext) {
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
    ) = withContext(queriesContext) {
        reactionsQueries.insertReaction(
            originalMessageId, conversationId, senderUserId, emoji, date
        )
    }

    override suspend fun deleteReaction(
        originalMessageId: String,
        conversationId: ConversationIDEntity,
        senderUserId: UserIDEntity,
        emoji: String
    ) = withContext(queriesContext) {
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

    override suspend fun observeMessageReactions(conversationId: QualifiedIDEntity, messageId: String): Flow<List<MessageReactionEntity>> =
        reactionsQueries.selectMessageReactionsByConversationIdAndMessageId(messageId, conversationId)
            .asFlow()
            .flowOn(queriesContext)
            .mapToList()
            .map { it.map(ReactionMapper::fromDAOToMessageReactionsEntity) }
}
