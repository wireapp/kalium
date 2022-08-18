package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationOptions
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.conversation.CreateGroupConversationUseCase.Result
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.datetime.Clock

/**
 * Creates a group conversation.
 * Will wait for sync to finish or fail if it is pending,
 * and return one [Result].
 */
class CreateGroupConversationUseCase internal constructor(
    private val conversationRepository: ConversationRepository,
    private val syncManager: SyncManager,
    private val clientRepository: ClientRepository
) {

    /**
     * @param name the name of the conversation
     * @param userIdList list of members
     * @param options settings that customise the conversation
     */
    suspend operator fun invoke(name: String, userIdList: List<UserId>, options: ConversationOptions): Result =
        syncManager.waitUntilLiveOrFailure().flatMap {
            clientRepository.currentClientId()
        }.flatMap { clientId ->
            conversationRepository.createGroupConversation(name, userIdList, options.copy(creatorClientId = clientId.value))
        }.flatMap { conversation ->
            conversationRepository.updateConversationModifiedDate(conversation.id, Clock.System.now().toString())
                .map { conversation }
        }.fold({
            if (it is NetworkFailure.NoNetworkConnection) {
                Result.SyncFailure
            } else {
                Result.UnknownFailure(it)
            }
        }, {
            Result.Success(it)
        })

    sealed interface Result {
        /**
         * Conversation created successfully.
         */
        class Success(
            /**
             * Details of the newly created conversation
             */
            val conversation: Conversation
        ) : Result

        /**
         * There was a failure trying to Sync with the server
         */
        object SyncFailure : Result

        /**
         * Other, unknown failure.
         */
        class UnknownFailure(
            /**
             * The root cause of the failure
             */
            val cause: CoreFailure
        ) : Result
    }
}
