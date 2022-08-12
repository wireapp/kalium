package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.AssetId
import com.wire.kalium.logic.functional.fold

class ClearConversationContentUseCase(
    private val conversationRepository: ConversationRepository,
    private val assetRepository: AssetRepository
) {

    suspend operator fun invoke(conversationId: ConversationId) {
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

            conversationRepository.deleteAllMessages(conversationId).fold({ Result.Failure }, { Result.Success })
        })
    }

    sealed class Result {
        object Success : Result()
        object Failure : Result()
    }

}
