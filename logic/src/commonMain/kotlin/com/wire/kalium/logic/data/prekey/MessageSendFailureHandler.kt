package com.wire.kalium.logic.data.prekey

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending

class MessageSendFailureHandler(
    private val userRepository: UserRepository,
    private val clientRepository: ClientRepository
) {
    /**
     * Handle a failure when attempting to send a message
     * due to contacts and/or clients being removed from conversation and/or added to them.
     * @return Either.Left if can't recover from error
     * @return Either.Right if the error was properly handled and a new attempt at sending message can be made
     */
    suspend fun handleClientsHaveChangedFailure(sendFailure: SendMessageFailure.ClientsHaveChanged): Either<CoreFailure, Unit> =
        suspending {
            //TODO Add/remove members to/from conversation
            //TODO remove clients from conversation
            userRepository.fetchUsersByIds(sendFailure.missingClientsOfUsers.keys).flatMap {
                sendFailure.missingClientsOfUsers.entries.foldToEitherWhileRight(Unit) { entry, _ ->
                    clientRepository.saveNewClients(entry.key, entry.value)
                }
            }
        }
}
