package com.wire.kalium.logic.feature.message

import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.cryptography.createSessions
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.prekey.QualifiedUserPreKeyInfo
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.ProteusClientProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.foldToEitherWhileRight
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapCryptoRequest

internal interface SessionEstablisher {

    /**
     * Verifies if this client can send messages to all the client recipients.
     * Will fetch PreKeys and establish cryptographic sessions if needed.
     */
    suspend fun prepareRecipientsForNewOutgoingMessage(
        recipients: List<Recipient>
    ): Either<CoreFailure, Unit>
}

internal class SessionEstablisherImpl internal constructor(
    private val proteusClientProvider: ProteusClientProvider,
    private val preKeyRepository: PreKeyRepository,
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : SessionEstablisher, CryptoSessionMapper by CryptoSessionMapperImpl() {
    override suspend fun prepareRecipientsForNewOutgoingMessage(
        recipients: List<Recipient>
    ): Either<CoreFailure, Unit> =
        getAllMissingClients(recipients).flatMap {
            establishMissingSessions(it)
        }

    private suspend fun establishMissingSessions(
        missingContactClients: Map<UserId, List<ClientId>>
    ): Either<CoreFailure, Unit> =
        if (missingContactClients.isEmpty()) {
            Either.Right(Unit)
        } else {
            preKeyRepository.preKeysOfClientsByQualifiedUsers(missingContactClients)
                .flatMap { preKeyInfoList -> establishSessions(preKeyInfoList) }
        }

    private suspend fun establishSessions(preKeyInfoList: List<QualifiedUserPreKeyInfo>): Either<CoreFailure, Unit> =
        proteusClientProvider.getOrError()
            .flatMap { proteusClient ->
                wrapCryptoRequest {
                    proteusClient.createSessions(getMapOfSessionIdsToPreKeysAndIgnoreNull(preKeyInfoList))
                }
            }

    private suspend fun getAllMissingClients(
        detailedContacts: List<Recipient>
    ): Either<CoreFailure, MutableMap<UserId, List<ClientId>>> =
        detailedContacts.foldToEitherWhileRight(mutableMapOf<UserId, List<ClientId>>()) { recipient, userAccumulator ->
            getMissingClientsForRecipients(recipient).map { missingClients ->
                if (missingClients.isNotEmpty()) {
                    userAccumulator[recipient.id] = missingClients
                }
                userAccumulator
            }
        }

    private suspend fun getMissingClientsForRecipients(
        recipient: Recipient
    ): Either<CoreFailure, MutableList<ClientId>> =
        recipient.clients.foldToEitherWhileRight(mutableListOf<ClientId>()) { client, clientIdAccumulator ->
            doesSessionExist(recipient.id, client).map { sessionExists ->
                if (!sessionExists) {
                    clientIdAccumulator += client
                }
                clientIdAccumulator
            }
        }

    private suspend fun doesSessionExist(
        recipientUserId: UserId,
        client: ClientId
    ): Either<CoreFailure, Boolean> =
        proteusClientProvider.getOrError()
            .flatMap { proteusClient ->
                val cryptoSessionID = CryptoSessionId(idMapper.toCryptoQualifiedIDId(recipientUserId), CryptoClientId(client.value))
                wrapCryptoRequest {
                    proteusClient.doesSessionExist(cryptoSessionID)
                }
            }
}

internal interface CryptoSessionMapper {
    fun getMapOfSessionIdsToPreKeysAndIgnoreNull(
        preKeyInfoList: List<QualifiedUserPreKeyInfo>
    ): Map<String, Map<String, Map<String, PreKeyCrypto>>>
}

internal class CryptoSessionMapperImpl internal constructor() : CryptoSessionMapper {
    override fun getMapOfSessionIdsToPreKeysAndIgnoreNull(
        preKeyInfoList: List<QualifiedUserPreKeyInfo>
    ): Map<String, Map<String, Map<String, PreKeyCrypto>>> {
        val acc = mutableMapOf<String, Map<String, Map<String, PreKeyCrypto>>>()
        preKeyInfoList.forEach {
            val userToClientsToPreKeyMap: Map<String, Map<String, PreKeyCrypto>> = preKeyInfoList.associate { userInfo ->
                userInfo.userId.value to userInfo.clientsInfo
                    .mapNotNull { clientPreKeyInfo ->
                        clientPreKeyInfo.preKey?.let { preKey -> clientPreKeyInfo.clientId to preKey }
                    }.toMap()
            }
            acc[it.userId.domain] = userToClientsToPreKeyMap
        }
        return acc
    }
}
