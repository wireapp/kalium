package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.EventMapper
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.sync.receiver.conversation.RenamedConversationEventHandler
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationRenameResponse
import com.wire.kalium.persistence.dao.message.LocalId
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Renames a conversation by its ID.
 */
interface RenameConversationUseCase {
    /**
     * @param conversationId the conversation id to rename
     * @param conversationName the new conversation name
     */
    suspend operator fun invoke(conversationId: ConversationId, conversationName: String): RenamingResult
}

internal class RenameConversationUseCaseImpl(
    val conversationRepository: ConversationRepository,
    val persistMessage: PersistMessageUseCase,
    private val renamedConversationEventHandler: RenamedConversationEventHandler,
    val selfUserId: UserId,
    private val eventMapper: EventMapper = MapperProvider.eventMapper(),
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : RenameConversationUseCase {
    override suspend fun invoke(conversationId: ConversationId, conversationName: String): RenamingResult =
        withContext(dispatcher.default) {
            conversationRepository.changeConversationName(conversationId, conversationName)
                .onSuccess { response ->
                    if (response is ConversationRenameResponse.Changed)
                        renamedConversationEventHandler.handle(
                            eventMapper.conversationRenamed(LocalId.generate(), response.event, true)
                        )
                }
                .fold({
                    RenamingResult.Failure(it)
                }, {
                    RenamingResult.Success
                })
        }
}

sealed class RenamingResult {
    object Success : RenamingResult()
    data class Failure(val coreFailure: CoreFailure) : RenamingResult()
}
