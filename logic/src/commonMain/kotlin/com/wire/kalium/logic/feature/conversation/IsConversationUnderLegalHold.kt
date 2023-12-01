/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.ClientType
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.foldToEitherWhileRight
import com.wire.kalium.logic.functional.map
import kotlinx.coroutines.flow.firstOrNull

/**
 * Checks if any client of members of given conversation id is under legal hold.
 */
interface IsConversationUnderLegalHold {
    suspend operator fun invoke(conversationId: ConversationId): Either<CoreFailure, Boolean>
}

internal class IsConversationUnderLegalHoldImpl(
    private val clientRepository: ClientRepository,
    private val conversationRepository: ConversationRepository,
) : IsConversationUnderLegalHold {
    override suspend fun invoke(conversationId: ConversationId): Either<CoreFailure, Boolean> =
        conversationRepository.getConversationMembers(conversationId)
            .flatMap<List<UserId>, StorageFailure, List<UserId>> { members ->
                members.foldToEitherWhileRight(emptyList()) { userId, acc ->
                    clientRepository.observeClientsByUserId(userId).firstOrNull()
                        .let { it ?: Either.Left(StorageFailure.DataNotFound) }
                        .map { if (it.any { it.type == ClientType.LegalHold }) acc + userId else acc }
                }
            }
            .map { it.isNotEmpty() }

}
