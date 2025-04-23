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
package com.wire.kalium.logic.feature.message.ephemeral

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.ASSETS
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import io.mockative.Mockable

/**
 * When the self user is the sender of the self deletion message, we only mark it as deleted (but we delete asset files locally),
 * because we are relying on the receiver, telling us when to delete the message permanently,
 * that is when the message has expired for one of the conversation members of GROUP or ONE_TO_ONE type
 * see [com.wire.kalium.logic.feature.message.ephemeral.DeleteEphemeralMessageForSelfUserAsReceiverUseCaseImpl] for details
 **/
@Mockable
internal interface DeleteEphemeralMessageForSelfUserAsSenderUseCase {
    /**
     * @param conversationId the conversation id that contains the self-deleting message
     * @param messageId the id of the self-deleting message
     */
    suspend operator fun invoke(conversationId: ConversationId, messageId: String): Either<CoreFailure, Unit>
}

internal class DeleteEphemeralMessageForSelfUserAsSenderUseCaseImpl internal constructor(
    private val messageRepository: MessageRepository,
    private val assetRepository: AssetRepository,
) : DeleteEphemeralMessageForSelfUserAsSenderUseCase {

    override suspend fun invoke(conversationId: ConversationId, messageId: String): Either<CoreFailure, Unit> =
        messageRepository.getMessageById(conversationId, messageId)
            .onSuccess { message ->
                deleteMessageAssetLocallyIfExists(message)
            }
            .flatMap {
                messageRepository.markMessageAsDeleted(messageId, conversationId)
            }

    private suspend fun deleteMessageAssetLocallyIfExists(message: Message) {
        (message.content as? MessageContent.Asset)?.value?.remoteData?.let { assetToRemove ->
            assetRepository.deleteAssetLocally(assetToRemove.assetId).onFailure {
                kaliumLogger.withFeatureId(ASSETS).w("delete message asset failure: $it")
            }
        }
    }
}
