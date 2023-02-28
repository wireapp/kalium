package com.wire.kalium.logic.sync.receiver.asset

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess

internal interface AssetMessageHandler {
    suspend fun handle(
        message: Message.Regular,
        messageContent: MessageContent.Asset
    )
}

internal class AssetMessageHandlerImpl(
    private val messageRepository: MessageRepository,
    private val persistMessage: PersistMessageUseCase,
    private val userConfigRepository: UserConfigRepository
) : AssetMessageHandler {

    override suspend fun handle(message: Message.Regular, messageContent: MessageContent.Asset) {
        userConfigRepository.isFileSharingEnabled().onSuccess {
            if (it.isFileSharingEnabled != null && it.isFileSharingEnabled) {
                processNonRestrictedAssetMessage(message, messageContent)
            } else {
                val newMessage = message.copy(
                    content = MessageContent.RestrictedAsset(
                        mimeType = messageContent.value.mimeType,
                        sizeInBytes = messageContent.value.sizeInBytes,
                        name = messageContent.value.name ?: ""
                    )
                )
                persistMessage(newMessage)
            }
        }
    }

    private suspend fun processNonRestrictedAssetMessage(message: Message.Regular, assetContent: MessageContent.Asset) {
        messageRepository.getMessageById(message.conversationId, message.id).onFailure {
            // No asset message was received previously, so just persist the preview of the asset message
            // Web/Mac clients split the asset message delivery into 2. One with the preview metadata (assetName, assetSize...) and
            // with empty encryption keys and the second with empty metadata but all the correct encryption keys. We just want to
            // hide the preview of generic asset messages with empty encryption keys as a way to avoid user interaction with them.
            val previewMessage = message.copy(
                visibility = if (assetContent.value.shouldBeDisplayed) Message.Visibility.VISIBLE else Message.Visibility.HIDDEN
            )
            persistMessage(previewMessage)
        }.onSuccess { persistedMessage ->
            val validDecryptionKeys = assetContent.value.remoteData
            // Check the second asset message is from the same original sender
            if (isSenderVerified(
                    persistedMessage.id,
                    persistedMessage.conversationId,
                    message.senderUserId
                ) && persistedMessage is Message.Regular
            ) {
                // The second asset message received from Web/Mac clients contains the full asset decryption keys, so we need to update
                // the preview message persisted previously with the rest of the data
                persistMessage(
                    updateAssetMessageWithDecryptionKeys(
                        persistedMessage,
                        validDecryptionKeys
                    )
                )
            }
        }
    }

    private suspend fun isSenderVerified(messageId: String, conversationId: ConversationId, senderUserId: UserId): Boolean {
        var verified = false

        messageRepository.getMessageById(
            messageUuid = messageId,
            conversationId = conversationId
        ).onSuccess {
            verified = senderUserId == it.senderUserId
        }

        return verified
    }

    private fun updateAssetMessageWithDecryptionKeys(
        persistedMessage: Message.Regular,
        remoteData: AssetContent.RemoteData
    ): Message.Regular {
        val assetMessageContent = persistedMessage.content as MessageContent.Asset
        // The message was previously received with just metadata info, so let's update it with the raw data info
        return persistedMessage.copy(
            content = assetMessageContent.copy(
                value = assetMessageContent.value.copy(
                    remoteData = remoteData
                )
            ),
            visibility = Message.Visibility.VISIBLE
        )
    }
}
