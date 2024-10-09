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
package com.wire.kalium.logic.feature.debug

import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.message.SessionEstablisher
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger

interface EstablishSessionUseCase {

    /**
     * Establish a proteus session with another client
     *
     * @param userId the id of the user to whom the session established
     * @param clientId the id of the client of the user to whom the session should be established
     * @return an [EstablishSessionResult] containing a [CoreFailure] in case anything goes wrong
     * and [EstablishSessionResult.Success] in case everything succeeds
     */
    suspend operator fun invoke(userId: UserId, clientId: ClientId): EstablishSessionResult
}

sealed class EstablishSessionResult {
    data object Success : EstablishSessionResult()
    data class Failure(val coreFailure: CoreFailure) : EstablishSessionResult()
}

internal class EstablishSessionUseCaseImpl(val sessionEstablisher: SessionEstablisher) : EstablishSessionUseCase {
    override suspend fun invoke(userId: UserId, clientId: ClientId): EstablishSessionResult {
        return sessionEstablisher.prepareRecipientsForNewOutgoingMessage(
            listOf(Recipient(id = userId, clients = listOf(clientId)))
        ).fold({
            kaliumLogger.e("Failed to get establish session $it")
            EstablishSessionResult.Failure(it)
        }, {
            kaliumLogger.d("Established session with ${userId.toLogString()} with ${clientId.value.obfuscateId()}")
            EstablishSessionResult.Success
        })
    }
}
