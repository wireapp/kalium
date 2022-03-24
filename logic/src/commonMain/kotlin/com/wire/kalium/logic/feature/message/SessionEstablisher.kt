package com.wire.kalium.logic.feature.message

import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.createSessions
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.ProteusFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.prekey.QualifiedUserPreKeyInfo
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.wrapCryptoRequest
import com.wire.kalium.cryptography.PlainUserId as CryptoUserId

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

    private suspend fun establishSessions(preKeyInfoList: List<QualifiedUserPreKeyInfo>): Either<ProteusFailure, Unit> =
        wrapCryptoRequest {
            proteusClient.createSessions(
                getMapOfSessionIdsToPreKeys(preKeyInfoList)
            )
        }


    private fun getMapOfSessionIdsToPreKeys(preKeyInfoList: List<QualifiedUserPreKeyInfo>) =
        preKeyInfoList.associate { userInfo ->
            userInfo.userId.value to userInfo.clientsInfo.associate { clientInfo ->
                clientInfo.clientId to clientInfo.preKey
            }
        }

    private suspend fun getAllMissingClients(
        detailedContacts: List<Recipient>
    ): Either<ProteusFailure, Map<UserId, List<ClientId>>> = suspending {
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
    ): Either<ProteusFailure, List<ClientId>> =
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
    ): Either<ProteusFailure, Boolean> =
        wrapCryptoRequest {
            val cryptoSessionID = CryptoSessionId(CryptoUserId(recipientUserId), CryptoClientId(client.value))
            proteusClient.doesSessionExist(cryptoSessionID)
        }
}
