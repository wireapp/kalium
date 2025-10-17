/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.asset

import com.wire.kalium.cells.domain.usecase.GetMessageAttachmentUseCase
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.MessageId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.CellAssetContent
import com.wire.kalium.logic.feature.client.IsWireCellsEnabledForConversationUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import okio.Path.Companion.toPath

/**
 * Use case to get audio asset either from Wire Cells or Asset Storage
 * based on whether Wire Cells is enabled for the conversation.
 */
interface GetAudioAssetUseCase {
    suspend operator fun invoke(
        conversationId: ConversationId,
        messageId: MessageId?,
        assetId: String?
    ): Deferred<MessageAssetResult>
}

internal class GetAudioAssetUseCaseImpl(
    private val isWireCellsEnabledForConversation: IsWireCellsEnabledForConversationUseCase,
    private val getMessageAsset: GetMessageAssetUseCase,
    private val getMessageAttachment: GetMessageAttachmentUseCase
) : GetAudioAssetUseCase {

    override suspend fun invoke(
        conversationId: ConversationId,
        messageId: MessageId?,
        assetId: String?
    ): Deferred<MessageAssetResult> {
        return if (isWireCellsEnabledForConversation(conversationId) && assetId != null) {
            getMessageAttachment(assetId).fold(
                {
                    kaliumLogger.i("There was an error getting attachment for assetId: ${assetId.obfuscateId()}. Error: $it")
                    CompletableDeferred(MessageAssetResult.Failure(it, false))
                },
                { message ->
                    when (message) {
                        is CellAssetContent -> {
                            CompletableDeferred(
                                MessageAssetResult.Success(
                                    message.localPath!!.toPath(),
                                    message.assetSize ?: 0L,
                                    message.localPath!!.substringAfterLast('/')
                                )
                            )
                        }

                        is AssetContent -> {
                            CompletableDeferred(
                                MessageAssetResult.Success(
                                    message.localData?.assetDataPath!!.toPath(),
                                    message.sizeInBytes,
                                    message.localData?.assetDataPath!!.substringAfterLast('/')
                                )
                            )
                        }
                    }
                }
            )
        } else {
            getMessageAsset(conversationId, messageId!!)
        }
    }
}
