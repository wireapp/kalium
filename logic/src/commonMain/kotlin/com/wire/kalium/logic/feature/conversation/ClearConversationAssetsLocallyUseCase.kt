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
package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap

interface ClearConversationAssetsLocallyUseCase {
    /**
     * Clear all conversation assets from local storage
     *
     * @param conversationId - id of conversation in which assets should be cleared
     */
    suspend operator fun invoke(conversationId: ConversationId): Either<CoreFailure, Unit>
}

internal class ClearConversationAssetsLocallyUseCaseImpl(
    private val messageRepository: MessageRepository,
    private val assetRepository: AssetRepository
) : ClearConversationAssetsLocallyUseCase {
    override suspend fun invoke(conversationId: ConversationId): Either<CoreFailure, Unit> {
        return messageRepository.getAllAssetIdsFromConversationId(conversationId)
            .flatMap { ids ->
                if (ids.isEmpty()) return Either.Right(Unit)

                ids.map { id -> assetRepository.deleteAssetLocally(id) }
                    .reduce { acc, either ->
                        acc.flatMap { either }
                    }
            }
    }
}
