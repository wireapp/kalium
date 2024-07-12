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

package com.wire.kalium.logic.data.message

import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.prekey.PreKeyMapper
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.prekey.UsersWithoutSessions
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.data.client.ProteusClientProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.foldToEitherWhileRight
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapProteusRequest
import com.wire.kalium.network.api.authenticated.prekey.PreKeyDTO
import com.wire.kalium.persistence.dao.QualifiedIDEntity

internal interface SessionEstablisher {

    /**
     * Verifies if this client can send messages to all the client recipients.
     * Will fetch PreKeys and establish cryptographic sessions if needed.
     *
     * @return an error or holder [UsersWithoutSessions] with an optional list of users whose sessions are missing.
     * Useful for sending a message to a partial list of users.
     */
    suspend fun prepareRecipientsForNewOutgoingMessage(
        recipients: List<Recipient>
    ): Either<CoreFailure, UsersWithoutSessions>
}

internal class SessionEstablisherImpl internal constructor(
    private val proteusClientProvider: ProteusClientProvider,
    private val preKeyRepository: PreKeyRepository,
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : SessionEstablisher {
    override suspend fun prepareRecipientsForNewOutgoingMessage(
        recipients: List<Recipient>
    ): Either<CoreFailure, UsersWithoutSessions> =
        getAllMissingClients(recipients).flatMap {
            if (it.isEmpty()) {
                return@flatMap Either.Right(UsersWithoutSessions.EMPTY)
            }
            preKeyRepository.establishSessions(it)
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
                wrapProteusRequest {
                    proteusClient.doesSessionExist(cryptoSessionID)
                }
            }
}

internal interface CryptoSessionMapper {
    fun getMapOfSessionIdsToPreKeysAndMarkNullClientsAsInvalid(
        preKeyInfoMap: Map<String, Map<String, Map<String, PreKeyDTO?>>>
    ): FilteredRecipient
}

data class FilteredRecipient(
    val valid: Map<String, Map<String, Map<String, PreKeyCrypto>>>,
    val invalid: List<Pair<QualifiedIDEntity, List<String>>>
)

internal class CryptoSessionMapperImpl internal constructor(
    private val preKeyMapper: PreKeyMapper
) : CryptoSessionMapper {
    override fun getMapOfSessionIdsToPreKeysAndMarkNullClientsAsInvalid(
        preKeyInfoMap: Map<String, Map<String, Map<String, PreKeyDTO?>>>
    ): FilteredRecipient {
        val invalidList: MutableList<Pair<QualifiedIDEntity, String>> = mutableListOf()
        val validAccumulator: Map<String, Map<String, Map<String, PreKeyCrypto>>> =
            preKeyInfoMap.mapValues { (domain, userIdToClientToPrekeyMap) ->
                userIdToClientToPrekeyMap.mapValues { (userId, clientIdToPreKeyMap) ->
                    clientIdToPreKeyMap.filter { (clientId, prekey) ->
                        if (prekey == null) {
                            invalidList.add(QualifiedIDEntity(userId, domain) to clientId)
                            false
                        } else {
                            true
                        }
                    }.mapValues { (_, prekeyDTO) ->
                        preKeyMapper.fromPreKeyDTO(prekeyDTO!!) // null pre-keys are filtered in the step above
                    }
                }
            }
        return FilteredRecipient(
            validAccumulator,
            invalidList.groupBy {
                it.first
            }.mapValues {
                it.value.map { it.second }
            }.toList()
        )
    }
}
