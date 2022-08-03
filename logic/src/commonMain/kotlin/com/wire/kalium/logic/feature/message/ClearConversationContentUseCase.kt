package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.AssetId
import com.wire.kalium.logic.functional.fold

internal class ClearConversationContentUseCase(
    private val conversationRepository: ConversationRepository,
    private val assetRepository: AssetRepository
) {

    suspend operator fun invoke(conversationId: ConversationId) =
        // delete all the assets related to the conversation first
        conversationRepository.getConversationAssetMessages(conversationId).fold({ Result.Failure }, { conversationAssetMessages ->
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

            // now we can proceed with deleting all messages related to the conversation
            conversationRepository.deleteAllMessages(conversationId).fold({ Result.Failure }, { Result.Success })
        })

    sealed class Result {
        object Success : Result()
        object Failure : Result()
    }

}
