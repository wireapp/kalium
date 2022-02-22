package com.wire.kalium.logic.feature.message

import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.createSessions
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.prekey.QualifiedUserPreKeyInfo
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.cryptography.UserId as CryptoUserId

interface SessionEstablisher {

    /**
     * Verifies if this client can send messages to all the client recipients.
     * Will fetch PreKeys and establish cryptographic sessions if needed.
     */
    suspend fun prepareRecipientsForNewOutgoingMessage(
        recipients: List<Recipient>
    ): Either<CoreFailure, Unit>
}

class SessionEstablisherImpl(
    private val proteusClient: ProteusClient,
    private val preKeyRepository: PreKeyRepository
) : SessionEstablisher {
    override suspend fun prepareRecipientsForNewOutgoingMessage(
        recipients: List<Recipient>
    ): Either<CoreFailure, Unit> =
        suspending {
            getAllMissingClients(recipients).flatMap {
                establishMissingSessions(it)
            }
        }

    private suspend fun establishMissingSessions(
        missingContactClients: Map<UserId, List<ClientId>>
    ): Either<CoreFailure, Unit> = suspending {
        if (missingContactClients.isEmpty()) {
            return@suspending Either.Right(Unit)
        }

        preKeyRepository.preKeysOfClientsByQualifiedUsers(missingContactClients)
            .flatMap { preKeyInfoList -> establishSessions(preKeyInfoList) }
    }

    private suspend fun establishSessions(preKeyInfoList: List<QualifiedUserPreKeyInfo>): Either<CoreFailure.Unknown, Unit> {
        val sessionPreKeysMap = getMapOfSessionIdsToPreKeys(preKeyInfoList)
        return try {
            proteusClient.createSessions(sessionPreKeysMap)
            Either.Right(Unit)
        } catch (proteusException: ProteusException) {
            Either.Left(CoreFailure.Unknown(proteusException))
        }
    }

    private fun getMapOfSessionIdsToPreKeys(preKeyInfoList: List<QualifiedUserPreKeyInfo>) =
        preKeyInfoList.map { userInfo ->
            userInfo.userId.value to userInfo.clientsInfo.map { clientInfo ->
                clientInfo.clientId to clientInfo.preKey
            }.toMap()
        }.toMap()

    private suspend fun getAllMissingClients(
        detailedContacts: List<Recipient>
    ): Either<CoreFailure, Map<UserId, List<ClientId>>> = suspending {
        detailedContacts.foldToEitherWhileRight(mutableMapOf<UserId, List<ClientId>>()) { recipient, userAccumulator ->
            getMissingClientsForRecipients(recipient).map { missingClients ->
                if (missingClients.isNotEmpty()) {
                    userAccumulator[recipient.member.id] = missingClients
                }
                userAccumulator
            }
        }
    }

    private suspend fun getMissingClientsForRecipients(
        recipient: Recipient
    ): Either<CoreFailure, List<ClientId>> =
        suspending {
            recipient.clients.foldToEitherWhileRight(mutableListOf<ClientId>()) { client, clientIdAccumulator ->
                //TODO Use domain too
                doesSessionExist(recipient.member.id.value, client).map { sessionExists ->
                    if (!sessionExists) {
                        clientIdAccumulator += client
                    }
                    clientIdAccumulator
                }
            }
        }

    private suspend fun doesSessionExist(
        recipientUserId: String,
        client: ClientId
    ): Either<CoreFailure, Boolean> {
        return try {
            val cryptoSessionID = CryptoSessionId(CryptoUserId(recipientUserId), CryptoClientId(client.value))
            Either.Right(proteusClient.doesSessionExist(cryptoSessionID))
        } catch (proteusException: ProteusException) {
            Either.Left(CoreFailure.Unknown(proteusException))
        }
    }
}
