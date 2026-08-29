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
import com.wire.kalium.logic.configuration.FileSharingStatusProvider
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.IncomingAssetMessageLookup
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.StoredIncomingAssetMessage
import com.wire.kalium.logic.data.message.hasValidData
import com.wire.kalium.logic.feature.asset.ValidateAssetFileTypeUseCase
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.util.InternalKaliumApi

@InternalKaliumApi
public interface AssetMessageHandler {
    public suspend fun handle(message: Message.Regular)
}

@InternalKaliumApi
public class AssetMessageHandlerImpl public constructor(
    private val incomingAssetMessageLookup: IncomingAssetMessageLookup,
    private val persistMessage: PersistMessageUseCase,
    private val fileSharingStatusProvider: FileSharingStatusProvider,
    private val validateAssetMimeTypeUseCase: ValidateAssetFileTypeUseCase
) : AssetMessageHandler {

    override suspend fun handle(message: Message.Regular) {
        if (message.content !is MessageContent.Asset) {
            kaliumLogger.e("The asset message trying to be processed has invalid content data")
            return
        }

        val messageContent = message.content as MessageContent.Asset

        fileSharingStatusProvider.isFileSharingEnabled().onSuccess {
            val isThisAssetAllowed = when (val restrictionState = it.state) {
                FileSharingStatus.Value.Disabled -> AssetRestrictionContinuationStrategy.Restrict
                FileSharingStatus.Value.EnabledAll -> AssetRestrictionContinuationStrategy.Continue

                is FileSharingStatus.Value.EnabledSome -> {
                    // If the asset message is missing the name, but it does have full
                    // asset data then we can not decide now if it is allowed or not
                    // it is safe to continue and the code later will check the original
                    // asset message and decide if it is allowed or not
                    if (validateAssetMimeTypeUseCase(
                            fileName = messageContent.value.name,
                            mimeType = messageContent.value.mimeType,
                            allowedExtension = restrictionState.allowedType
                        )
                    ) {
                        AssetRestrictionContinuationStrategy.Continue
                    } else {
                        if (messageContent.value.name.isNullOrEmpty() && messageContent.value.isAssetDataComplete) {
                            AssetRestrictionContinuationStrategy.RestrictIfThereIsNotOldMessageWithTheSameAssetID
                        } else {
                            AssetRestrictionContinuationStrategy.Restrict
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
        incomingAssetMessageLookup.getMessageById(processedMessage.conversationId, processedMessage.id).onFailure {
            // No asset message was received previously, so just persist the preview of the asset message
            // Web/Mac clients split the asset message delivery into 2. One with the preview metadata (assetName, assetSize...) and
            // with empty encryption keys and the second with empty metadata but all the correct encryption keys. We just want to
            // hide the preview of generic asset messages with empty encryption keys as a way to avoid user interaction with them.

            if (restrictIfNotAFollowUpMessage) {
                persistRestrictedAssetMessage(processedMessage, assetContent)
            } else {
                val initialMessage = processedMessage.copy(
                    visibility = if (assetContent.value.isAssetDataComplete) Message.Visibility.VISIBLE else Message.Visibility.HIDDEN
                )
                persistMessage(initialMessage)
            }
        }.onSuccess { persistedMessage ->
            val validDecryptionKeys = assetContent.value.remoteData
            when {
                persistedMessage.senderUserId != processedMessage.senderUserId ||
                    persistedMessage is StoredIncomingAssetMessage.System -> {
                    kaliumLogger.e("The previously persisted message has a different sender id than the one we are trying to process")
                }

                persistedMessage is StoredIncomingAssetMessage.RegularAsset -> {
                    // The second asset message received from Web/Mac clients contains the full asset decryption keys, so we need to update
                    // the preview message persisted previously with the rest of the data
                    updateAssetMessageWithDecryptionKeys(persistedMessage.message, validDecryptionKeys).let {
                        persistMessage(it)
                    }
                }

                persistedMessage is StoredIncomingAssetMessage.UnsupportedRegular -> {
                    kaliumLogger.e(
                        "Invalid asset message content type=${persistedMessage.contentType} " +
                            "messageId=${persistedMessage.messageId} conversationId=${persistedMessage.conversationId}"
                    )
                }

                persistedMessage is StoredIncomingAssetMessage.RestrictedAsset -> Unit
            }
        }
    }

    private fun updateAssetMessageWithDecryptionKeys(
        persistedMessage: Message.Regular,
        remoteData: AssetContent.RemoteData
    ): Message.Regular {
        val assetContent = persistedMessage.content as MessageContent.Asset
        return persistedMessage.copy(
            content = assetContent.copy(
                value = assetContent.value.copy(
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
