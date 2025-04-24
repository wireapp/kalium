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

package com.wire.kalium.logic.feature.conversation.keyingmaterials

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.UpdateKeyingMaterialThresholdProvider
import com.wire.kalium.logic.data.id.ConversationIdWithGroup

sealed class UpdateKeyingMaterialsResult {

    data object Success : UpdateKeyingMaterialsResult()
    data class Failure(val failure: CoreFailure) : UpdateKeyingMaterialsResult()

}

/**
 * This use case will check if the number of keying materials is below the minimum threshold and will
 * upload new keying materials if needed for the mls conversations of the user.
 */
interface UpdateKeyingMaterialsUseCase {
    suspend operator fun invoke(): UpdateKeyingMaterialsResult
}

internal class UpdateKeyingMaterialsUseCaseImpl(
    private val mlsConversationRepository: MLSConversationRepository,
    private val conversationRepository: ConversationRepository,
    private val updateKeyingMaterialThresholdProvider: UpdateKeyingMaterialThresholdProvider
) : UpdateKeyingMaterialsUseCase {

    override suspend fun invoke(): UpdateKeyingMaterialsResult {
        return mlsConversationRepository
            .getMLSGroupsRequiringKeyingMaterialUpdate(updateKeyingMaterialThresholdProvider.keyingMaterialUpdateThreshold)
            .flatMap { groups ->
                groups.map { conversationIdWithGroup ->
                    mlsConversationRepository.updateKeyingMaterial(conversationIdWithGroup.groupId)
                        .flatMapLeft { failure ->
                            if (failure is MLSFailure.PendingCommitExist) {
                                kaliumLogger.e(
                                    "Pending commit exist for group ${conversationIdWithGroup.groupId}, trying to recover from it"
                                )
                                recoverFromPendingCommit(conversationIdWithGroup)
                            } else {
                                Either.Left(failure)
                            }
                        }
                }
                    .foldToEitherWhileRight(Unit) { value, _ -> value }
            }.fold(
                { UpdateKeyingMaterialsResult.Failure(it) },
                { UpdateKeyingMaterialsResult.Success }
            )
    }

    private suspend fun recoverFromPendingCommit(conversationIdWithGroup: ConversationIdWithGroup): Either<CoreFailure, Unit> =
        mlsConversationRepository.leaveGroup(conversationIdWithGroup.groupId)
            .flatMap {
                conversationRepository.getGroupInfo(conversationIdWithGroup.conversationId)
                    .flatMap {
                        mlsConversationRepository.joinGroupByExternalCommit(conversationIdWithGroup.groupId, it)
                    }
            }
            .flatMap {
                mlsConversationRepository.updateKeyingMaterial(conversationIdWithGroup.groupId)
            }

}
