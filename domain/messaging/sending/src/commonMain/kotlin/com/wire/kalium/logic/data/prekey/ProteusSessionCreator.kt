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

package com.wire.kalium.logic.data.prekey

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapProteusRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.map
import com.wire.kalium.cryptography.ProteusCoreCryptoContext
import com.wire.kalium.cryptography.createSessions
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.CryptoSessionMapper
import com.wire.kalium.logic.data.message.CryptoSessionMapperImpl
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.base.authenticated.prekey.DomainToUserIdToClientsToPreKeyMap
import com.wire.kalium.network.api.base.authenticated.prekey.PreKeyApi
import com.wire.kalium.persistence.dao.client.ClientDAO

/**
 * Creates Proteus sessions for clients that have none: fetches their pre-keys, creates the sessions,
 * and marks clients the backend returned no pre-key for as invalid.
 */
class ProteusSessionCreator(
    private val preKeyApi: PreKeyApi,
    private val clientDAO: ClientDAO,
    private val cryptoSessionMapper: CryptoSessionMapper = CryptoSessionMapperImpl(PreKeyMapperImpl()),
) {
    /** Returns the users whose pre-keys the backend could not list, for sending to a partial set of users. */
    suspend fun establishSessions(
        proteusContext: ProteusCoreCryptoContext,
        missingContactClients: Map<UserId, List<ClientId>>
    ): Either<CoreFailure, UsersWithoutSessions> {
        if (missingContactClients.isEmpty()) {
            return Either.Right(UsersWithoutSessions.EMPTY)
        }
        val request = missingContactClients.entries.groupBy { it.key.domain }.mapValues { domainEntry ->
            domainEntry.value.associate { userEntry -> userEntry.key.value to userEntry.value.map { it.value } }
        }
        return wrapApiRequest { preKeyApi.getUsersPreKey(request) }.flatMap { response ->
            establishProteusSessions(proteusContext, response.qualifiedUserClientPrekeys).map {
                UsersWithoutSessions(response.failedToList?.map { it.toModel() } ?: listOf())
            }
        }
    }

    private suspend fun establishProteusSessions(
        proteusContext: ProteusCoreCryptoContext,
        preKeyInfoList: DomainToUserIdToClientsToPreKeyMap
    ): Either<CoreFailure, Unit> {
        val (valid, invalid) = cryptoSessionMapper.getMapOfSessionIdsToPreKeysAndMarkNullClientsAsInvalid(preKeyInfoList)
        return wrapProteusRequest {
            proteusContext.createSessions(valid)
        }.also {
            wrapStorageRequest {
                clientDAO.tryMarkInvalid(invalid)
            }
        }
    }
}
