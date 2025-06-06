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
package com.wire.kalium.logic.util.arrangement.repository

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.OtherUserClient
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.functional.Either
import com.wire.kalium.persistence.dao.client.InsertClientParam
import io.mockative.any
import io.mockative.coEvery
import io.mockative.fake.valueOf
import io.mockative.matchers.AnyMatcher
import io.mockative.matchers.Matcher
import io.mockative.matches
import io.mockative.mock

internal interface ClientRepositoryArrangement {
    val clientRepository: ClientRepository

    suspend fun withUpdateClientProteusVerificationStatus(result: Either<StorageFailure, Unit>): ClientRepositoryArrangementImpl
    suspend fun withClientsByUserId(result: Either<StorageFailure, List<OtherUserClient>>): ClientRepositoryArrangementImpl
    suspend fun withRemoveClientsAndReturnUsersWithNoClients(
        result: Either<StorageFailure, List<UserId>>,
        redundantClientsOfUsers: Matcher<Map<UserId, List<ClientId>>> = AnyMatcher(valueOf())
    )

    suspend fun withStoreUserClientIdList(
        result: Either<StorageFailure, Unit>,
        userId: Matcher<UserId> = AnyMatcher(valueOf()),
        clientIds: Matcher<List<ClientId>> = AnyMatcher(valueOf())
    )

    suspend fun withStoreMapOfUserToClientId(
        result: Either<StorageFailure, Unit>,
        mapUserToClientId: Matcher<Map<UserId, List<ClientId>>> = AnyMatcher(valueOf())
    )

    suspend fun withStoreUserClientListAndRemoveRedundantClients(
        result: Either<StorageFailure, Unit>,
        clients: Matcher<List<InsertClientParam>> = AnyMatcher(valueOf())
    )

    suspend fun withSelfClientsResult(result: Either<NetworkFailure, List<Client>>)
}

internal open class ClientRepositoryArrangementImpl : ClientRepositoryArrangement {

    override val clientRepository: ClientRepository = mock(ClientRepository::class)

    override suspend fun withUpdateClientProteusVerificationStatus(result: Either<StorageFailure, Unit>) = apply {
        coEvery {
            clientRepository.updateClientProteusVerificationStatus(any(), any(), any())
        }.returns(result)
    }

    override suspend fun withClientsByUserId(result: Either<StorageFailure, List<OtherUserClient>>) = apply {
        coEvery {
            clientRepository.getClientsByUserId(any())
        }.returns(result)
    }

    override suspend fun withRemoveClientsAndReturnUsersWithNoClients(
        result: Either<StorageFailure, List<UserId>>,
        redundantClientsOfUsers: Matcher<Map<UserId, List<ClientId>>>
    ) {
        coEvery {
            clientRepository.removeClientsAndReturnUsersWithNoClients(matches { redundantClientsOfUsers.matches(it) })
        }.returns(result)
    }

    override suspend fun withStoreUserClientIdList(
        result: Either<StorageFailure, Unit>,
        userId: Matcher<UserId>,
        clientIds: Matcher<List<ClientId>>
    ) {
        coEvery {
            clientRepository.storeUserClientIdList(any(), any())
        }.returns(result)
    }

    override suspend fun withStoreMapOfUserToClientId(
        result: Either<StorageFailure, Unit>,
        mapUserToClientId: Matcher<Map<UserId, List<ClientId>>>
    ) {
        coEvery {
            clientRepository.storeMapOfUserToClientId(matches { mapUserToClientId.matches(it) })
        }.returns(result)
    }

    override suspend fun withStoreUserClientListAndRemoveRedundantClients(
        result: Either<StorageFailure, Unit>,
        clients: Matcher<List<InsertClientParam>>
    ) {
        coEvery {
            clientRepository.storeUserClientListAndRemoveRedundantClients(any())
        }.returns(result)
    }

    override suspend fun withSelfClientsResult(result: Either<NetworkFailure, List<Client>>) {
        coEvery { clientRepository.selfListOfClients() }.returns(result)
    }
}
