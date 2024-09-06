/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.wire.kalium.logic.sync.receiver.asset

import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.getType
import com.wire.kalium.logic.feature.asset.ValidateAssetFileTypeUseCase
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.receiver.conversation.message.hasValidData

internal interface AssetMessageHandler {
    suspend fun handle(message: Message.Regular)
}

internal class AssetMessageHandlerImpl(
    private val messageRepository: MessageRepository,
    private val persistMessage: PersistMessageUseCase,
    private val userConfigRepository: UserConfigRepository,
    private val validateAssetMimeTypeUseCase: ValidateAssetFileTypeUseCase
) : AssetMessageHandler {

    override suspend fun handle(message: Message.Regular) {
        if (message.content !is MessageContent.Asset) {
            kaliumLogger.e("The asset message trying to be processed has invalid content data")
            return
        }

        val messageContent = message.content
        userConfigRepository.isFileSharingEnabled().onSuccess {
            val isThisAssetAllowed = when (it.state) {
                FileSharingStatus.Value.Disabled -> AssetRestrictionContinuationStrategy.Restrict
                FileSharingStatus.Value.EnabledAll -> AssetRestrictionContinuationStrategy.Continue

                is FileSharingStatus.Value.EnabledSome -> {
                    // If the asset message is missing the name, but it does have full
                    // asset data then we can not decide now if it is allowed or not
                    // it is safe to continue and the code later will check the original
                    // asset message and decide if it is allowed or not
                    if (
                        message.content.value.name.isNullOrEmpty() &&
                        message.content.value.mimeType.isBlank() &&
                        message.content.value.isCompleteAssetData
                    ) {
                        kaliumLogger.e("The asset message trying to be processed has invalid data looking locally")
                        AssetRestrictionContinuationStrategy.RestrictIfThereIsNotOldMessageWithTheSameAssetID
                    } else {
                        validateAssetMimeTypeUseCase(
                            fileName = messageContent.value.name,
                            mimeType = messageContent.value.mimeType,
                            allowedExtension = it.state.allowedType
                        ).let { validateResult ->
                            if (validateResult) {
                                AssetRestrictionContinuationStrategy.Continue
                            } else {
                                AssetRestrictionContinuationStrategy.Restrict
                            }
                        }
                    }
                }
            }

            when (isThisAssetAllowed) {
                AssetRestrictionContinuationStrategy.Continue -> processNonRestrictedAssetMessage(message, messageContent, false)
                AssetRestrictionContinuationStrategy.RestrictIfThereIsNotOldMessageWithTheSameAssetID -> processNonRestrictedAssetMessage(
                    message,
                    messageContent,
                    true
                )

                AssetRestrictionContinuationStrategy.Restrict -> persistRestrictedAssetMessage(message, messageContent)

            }
        }
    }

    private suspend fun persistRestrictedAssetMessage(message: Message.Regular, messageContent: MessageContent.Asset) {
        val newMessage = message.copy(
            content = MessageContent.RestrictedAsset(
                mimeType = messageContent.value.mimeType,
                sizeInBytes = messageContent.value.sizeInBytes,
                name = messageContent.value.name ?: ""
            )
        )
        persistMessage(newMessage)
    }

    private suspend fun processNonRestrictedAssetMessage(
        processedMessage: Message.Regular,
        assetContent: MessageContent.Asset,
        restrictIfNotAFollowUpMessage: Boolean
    ) {
        messageRepository.getMessageById(processedMessage.conversationId, processedMessage.id).onFailure {
            // No asset message was received previously, so just persist the preview of the asset message
            // Web/Mac clients split the asset message delivery into 2. One with the preview metadata (assetName, assetSize...) and
            // with empty encryption keys and the second with empty metadata but all the correct encryption keys. We just want to
            // hide the preview of generic asset messages with empty encryption keys as a way to avoid user interaction with them.

            if (restrictIfNotAFollowUpMessage) {
                persistRestrictedAssetMessage(processedMessage, assetContent)
            } else {
                val initialMessage = processedMessage.copy(
                    visibility = if (assetContent.value.isCompleteAssetData) Message.Visibility.VISIBLE else Message.Visibility.HIDDEN
                )
                persistMessage(initialMessage)
            }
        }.onSuccess { persistedMessage ->
            val validDecryptionKeys = assetContent.value.remoteData
            // Check the second asset message is from the same original sender
            if (isSenderVerified(persistedMessage, processedMessage) && persistedMessage is Message.Regular) {
                // The second asset message received from Web/Mac clients contains the full asset decryption keys, so we need to update
                // the preview message persisted previously with the rest of the data
                updateAssetMessageWithDecryptionKeys(persistedMessage, validDecryptionKeys)?.let {
                    persistMessage(it)
                }
            } else {
                kaliumLogger.e("The previously persisted message has a different sender id than the one we are trying to process")
            }
        }
    }

    private fun isSenderVerified(persistedMessage: Message, processedMessage: Message): Boolean =
        persistedMessage.senderUserId == processedMessage.senderUserId

    private fun updateAssetMessageWithDecryptionKeys(
        persistedMessage: Message.Regular,
        remoteData: AssetContent.RemoteData
    ): Message.Regular? {
        val assetMessageContent = when (persistedMessage.content) {
            is MessageContent.Asset -> persistedMessage.content
            is MessageContent.RestrictedAsset -> {
                // original message was a restricted asset message, ignoring
                return null
            }

            is MessageContent.FailedDecryption,
            is MessageContent.Knock,
            is MessageContent.Location,
            is MessageContent.Composite,
            is MessageContent.Text,
            is MessageContent.Unknown -> error("Invalid asset message content type ${persistedMessage.content.getType()}")
        }
        // The message was previously received with just metadata info, so let's update it with the raw data info
        return persistedMessage.copy(
            content = assetMessageContent.copy(
                value = assetMessageContent.value.copy(
                    remoteData = remoteData
                )
            ),
            // If update message for any reason has still invalid encryption keys, message can't still be shown
            visibility = if (remoteData.hasValidData()) Message.Visibility.VISIBLE else Message.Visibility.HIDDEN
        )
    }
}

private sealed interface AssetRestrictionContinuationStrategy {
    data object Continue : AssetRestrictionContinuationStrategy
    data object Restrict : AssetRestrictionContinuationStrategy
    data object RestrictIfThereIsNotOldMessageWithTheSameAssetID : AssetRestrictionContinuationStrategy
}
