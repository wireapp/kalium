package com.wire.kalium.logic.feature.conversation

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.AssetId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import kotlinx.datetime.Clock

internal interface ClearConversationContent {
    suspend operator fun invoke(conversationId: ConversationId): Either<CoreFailure, Unit>
}

internal class ClearConversationContentImpl(
    private val conversationRepository: ConversationRepository,
    private val assetRepository: AssetRepository
) : ClearConversationContent {

    override suspend operator fun invoke(conversationId: ConversationId): Either<CoreFailure, Unit> {
        return conversationRepository.getAssetMessages(conversationId).flatMap { conversationAssetMessages ->
            conversationAssetMessages.forEach { message ->
                val messageContent: MessageContent = message.content

                if (messageContent is MessageContent.Asset) {
                    with(messageContent.value.remoteData) {
                        assetRepository.deleteAssetLocally(
                            AssetId(
                                assetId,
                                assetDomain.orEmpty()
                            )
                        )
                    }
                }
            }

            conversationRepository.deleteAllMessages(conversationId)
        }
    }
}

interface ClearConversationContentUseCase {
    suspend operator fun invoke(conversationId: ConversationId): Result

    sealed class Result {
        object Success : Result()
        object Failure : Result()
    }
}

internal class ClearConversationContentUseCaseImpl(
    private val clearConversationContent: ClearConversationContent,
    private val clientRepository: ClientRepository,
    private val messageSender: MessageSender,
    private val selfUserId: UserId,
    private val selfConversationIdProvider: SelfConversationIdProvider
) : ClearConversationContentUseCase {

    override suspend fun invoke(conversationId: ConversationId): ClearConversationContentUseCase.Result =
        clearConversationContent(conversationId).flatMap {
            clientRepository.currentClientId().flatMap { currentClientId ->
                selfConversationIdProvider().flatMap { selfConversationId ->
                    val regularMessage = Message.Regular(
                        id = uuid4().toString(),
                        content = MessageContent.Cleared(
                            unqualifiedConversationId = conversationId.value,
                            // the id of the conversation that we want to clear
                            conversationId = conversationId,
                            time = Clock.System.now()
                        ),
                        // sending the message to clear this conversation
                        conversationId = selfConversationId,
                        date = Clock.System.now().toString(),
                        senderUserId = selfUserId,
                        senderClientId = currentClientId,
                        status = Message.Status.PENDING,
                        editStatus = Message.EditStatus.NotEdited,
                    )
                    messageSender.sendMessage(regularMessage)
                }
            }
        }.fold({ ClearConversationContentUseCase.Result.Failure }, { ClearConversationContentUseCase.Result.Success })
}
