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
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.ClientType
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map

/**
 * Checks if any client of members of given conversation id is under legal hold.
 */
interface IsConversationUnderLegalHoldUseCase {
    suspend operator fun invoke(conversationId: ConversationId): Either<CoreFailure, Boolean>
}

internal class IsConversationUnderLegalHoldUseCaseImpl(
    private val clientRepository: ClientRepository,
) : IsConversationUnderLegalHoldUseCase {
    override suspend fun invoke(conversationId: ConversationId): Either<CoreFailure, Boolean> =
        clientRepository.getClientsByConversationId(conversationId)
            .map { it.values.flatten().any { it.type == ClientType.LegalHold } }
}
