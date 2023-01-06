package com.wire.kalium.logic.feature.conversation

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationOptions
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.feature.conversation.CreateGroupConversationUseCase.Result
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.util.DateTimeUtil

/**
 * Creates a group conversation.
 * Will wait for sync to finish or fail if it is pending,
 * and return one [Result].
 */
@Suppress("LongParameterList")
class CreateGroupConversationUseCase internal constructor(
    private val conversationRepository: ConversationRepository,
    private val conversationGroupRepository: ConversationGroupRepository,
    private val syncManager: SyncManager,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val selfUserId: UserId,
    val persistMessage: PersistMessageUseCase
) {

    /**
     * @param name the name of the conversation
     * @param userIdList list of members
     * @param options settings that customise the conversation
     */
    suspend operator fun invoke(name: String, userIdList: List<UserId>, options: ConversationOptions): Result =
        syncManager.waitUntilLiveOrFailure().flatMap {
            currentClientIdProvider()
        }.flatMap { clientId ->
            conversationGroupRepository.createGroupConversation(name, userIdList, options.copy(creatorClientId = clientId))
        }.flatMap { conversation ->
            conversationRepository.updateConversationModifiedDate(conversation.id, DateTimeUtil.currentIsoDateTimeString())
                .map { conversation }
        }.fold({
            if (it is NetworkFailure.NoNetworkConnection) {
                Result.SyncFailure
            } else {
                Result.UnknownFailure(it)
            }
        }, {
            handleSystemMessage(
                conversation = it,
                receiptModeEnabled = options.readReceiptsEnabled
            )
            Result.Success(it)
        })

    private suspend fun handleSystemMessage(
        conversation: Conversation,
        receiptModeEnabled: Boolean?
    ) = receiptModeEnabled?.let { receiptMode ->
        val message = Message.System(
            uuid4().toString(),
            MessageContent.NewConversationReceiptMode(
                receiptMode = receiptMode
            ),
            conversation.id,
            DateTimeUtil.currentIsoDateTimeString(),
            selfUserId,
            Message.Status.SENT,
            Message.Visibility.VISIBLE
        )

        persistMessage(message)
    }

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
