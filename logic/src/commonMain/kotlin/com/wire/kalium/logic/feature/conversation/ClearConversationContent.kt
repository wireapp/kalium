package com.wire.kalium.logic.feature.conversation

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.AssetId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import kotlinx.datetime.Clock

internal class ClearConversationContent(
    private val conversationRepository: ConversationRepository,
    private val assetRepository: AssetRepository
) {

    suspend operator fun invoke(conversationId: ConversationId): Either<CoreFailure, Unit> {
        return conversationRepository.getAssetMessages(conversationId).flatMap { conversationAssetMessages ->
            conversationAssetMessages.forEach { assetMessage ->
                val assetRemoteData = (assetMessage.content as MessageContent.Asset).value.remoteData

                with(assetRemoteData) {
                    assetRepository.deleteAsset(
                        AssetId(
                            assetId,
                            assetDomain.orEmpty()
                        ), assetToken
                    )
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
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val messageSender: MessageSender
) : ClearConversationContentUseCase {

    override suspend fun invoke(conversationId: ConversationId): ClearConversationContentUseCase.Result =
        clearConversationContent(conversationId).flatMap {
            clientRepository.currentClientId().flatMap { currentClientId ->
                val regularMessage = Message.Regular(
                    id = uuid4().toString(),
                    content = MessageContent.Cleared(
                        unqualifiedConversationId = conversationId.value,
                        conversationId = conversationId,
                        time = Clock.System.now()
                    ),
                    conversationId = conversationRepository.getSelfConversationId(),
                    date = Clock.System.now().toString(),
                    senderUserId = userRepository.getSelfUserId(),
                    senderClientId = currentClientId,
                    status = Message.Status.PENDING,
                    editStatus = Message.EditStatus.NotEdited,
                )
                messageSender.sendMessage(regularMessage)
            }
        }.fold({ ClearConversationContentUseCase.Result.Failure }, { ClearConversationContentUseCase.Result.Success })
}
