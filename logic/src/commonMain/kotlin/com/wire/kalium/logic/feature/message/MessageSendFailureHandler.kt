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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.failure.ProteusSendMessageFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.foldToEitherWhileRight

interface MessageSendFailureHandler {
    /**
     * Handle a failure when attempting to send a message
     * due to contacts and/or clients being removed from conversation and/or added to them.
     * @return Either.Left if can't recover from error
     * @return Either.Right if the error was properly handled and a new attempt at sending message can be made
     */
    suspend fun handleClientsHaveChangedFailure(sendFailure: ProteusSendMessageFailure): Either<CoreFailure, Unit>
}

class MessageSendFailureHandlerImpl internal constructor(
    private val userRepository: UserRepository,
    private val clientRepository: ClientRepository
) : MessageSendFailureHandler {

    override suspend fun handleClientsHaveChangedFailure(sendFailure: ProteusSendMessageFailure): Either<CoreFailure, Unit> =
    // TODO(optimization): add/remove members to/from conversation
        // TODO(optimization): remove clients from conversation
        userRepository
            .fetchUsersByIds(sendFailure.missingClientsOfUsers.keys)
            .flatMap {
                sendFailure
                    .missingClientsOfUsers
                    .entries
                    .foldToEitherWhileRight(Unit) { entry, _ ->
                        clientRepository.storeUserClientIdList(entry.key, entry.value)
                    }
            }
}
