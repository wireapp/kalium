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
package com.wire.kalium.logic.feature.legalhold

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.DeviceType
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map

/**
 * Returns list of ids of conversation members having a legal hold client.
 */
interface MembersHavingLegalHoldClientUseCase {
    suspend operator fun invoke(conversationId: ConversationId): Either<CoreFailure, List<UserId>>
}

internal class MembersHavingLegalHoldClientUseCaseImpl(
    private val clientRepository: ClientRepository,
) : MembersHavingLegalHoldClientUseCase {
    override suspend fun invoke(conversationId: ConversationId): Either<CoreFailure, List<UserId>> =
        clientRepository.getClientsByConversationId(conversationId)
            .map { it.filterValues { it.any { it.deviceType == DeviceType.LegalHold } }.keys.toList() }
}
