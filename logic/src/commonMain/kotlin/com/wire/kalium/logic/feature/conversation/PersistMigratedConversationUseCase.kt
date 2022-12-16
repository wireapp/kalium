package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.CONVERSATIONS
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationMapper
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.dao.MigrationDAO

/**
 * Persists a list of conversations migrated from old clients
 * Use carefully since normal conversations should come from the backend sync process
 *
 * @see [SyncConversationsUseCase]
 * @see [com.wire.kalium.logic.sync.SyncManager]
 */
fun interface PersistMigratedConversationUseCase {
    /**
     * Operation that persists a list of migrated conversations
     *
     * @param conversations list of migrated conversations
     * @return true or false depending on success operation
     */
    suspend operator fun invoke(conversations: List<Conversation>): Boolean
}

internal class PersistMigratedConversationUseCaseImpl(
    private val migrationDAO: MigrationDAO,
    private val conversationMapper: ConversationMapper
) : PersistMigratedConversationUseCase {

    val logger by lazy { kaliumLogger.withFeatureId(CONVERSATIONS) }

    override suspend fun invoke(conversations: List<Conversation>): Boolean {
        return conversations.map { conversationMapper.toDaoModel(it) }.let {
            wrapStorageRequest { migrationDAO.insertConversation(it) }.fold({
                logger.e("Error while persisting migrated conversations $it")
                false
            }) { true }
        }
    }
}
