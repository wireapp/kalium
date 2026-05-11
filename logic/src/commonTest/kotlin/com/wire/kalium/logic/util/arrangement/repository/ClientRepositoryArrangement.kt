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
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.matcher.matches
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.mock

private val MOCKATIVE_USER_ID = UserId("mockative-user", "mockative.test")
private val MOCKATIVE_CLIENT_ID = ClientId("mockative-client")

internal interface ClientRepositoryArrangement {
    val clientRepository: ClientRepository

    suspend fun withUpdateClientProteusVerificationStatus(result: Either<StorageFailure, Unit>): ClientRepositoryArrangementImpl
    suspend fun withClientsByUserId(result: Either<StorageFailure, List<OtherUserClient>>): ClientRepositoryArrangementImpl
    suspend fun withRemoveClientsAndReturnUsersWithNoClients(
        result: Either<StorageFailure, List<UserId>>,
        redundantClientsOfUsers: (Map<UserId, List<ClientId>>) -> Boolean = { true }
    )

    suspend fun withStoreUserClientIdList(
        result: Either<StorageFailure, Unit>,
        userId: (UserId) -> Boolean = { true },
        clientIds: (List<ClientId>) -> Boolean = { true }
    )

    suspend fun withStoreMapOfUserToClientId(
        result: Either<StorageFailure, Unit>,
        mapUserToClientId: (Map<UserId, List<ClientId>>) -> Boolean = { true }
    )

    suspend fun withStoreUserClientListAndRemoveRedundantClients(
        result: Either<StorageFailure, Unit>,
        clients: (List<InsertClientParam>) -> Boolean = { true }
    )

    suspend fun withSelfClientsResult(result: Either<NetworkFailure, List<Client>>)
}

internal open class ClientRepositoryArrangementImpl : ClientRepositoryArrangement {

    override val clientRepository: ClientRepository = mock<ClientRepository>(mode = MockMode.autoUnit)

    override suspend fun withUpdateClientProteusVerificationStatus(result: Either<StorageFailure, Unit>) = apply {
        everySuspend {
            clientRepository.updateClientProteusVerificationStatus(any(), any(), any())
        }.returns(result)
    }

    override suspend fun withClientsByUserId(result: Either<StorageFailure, List<OtherUserClient>>) = apply {
        everySuspend {
            clientRepository.getClientsByUserId(any())
        }.returns(result)
    }

    override suspend fun withRemoveClientsAndReturnUsersWithNoClients(
        result: Either<StorageFailure, List<UserId>>,
        redundantClientsOfUsers: (Map<UserId, List<ClientId>>) -> Boolean
    ) {
        everySuspend {
            clientRepository.removeClientsAndReturnUsersWithNoClients(matches { redundantClientsOfUsers(it) })
        }.returns(result)
    }

    override suspend fun withStoreUserClientIdList(
        result: Either<StorageFailure, Unit>,
        userId: (UserId) -> Boolean,
        clientIds: (List<ClientId>) -> Boolean
    ) {
        everySuspend {
            clientRepository.storeUserClientIdList(
                matches { userId(it) },
                matches { clientIds(it) }
            )
        }.returns(result)
    }

    override suspend fun withStoreMapOfUserToClientId(
        result: Either<StorageFailure, Unit>,
        mapUserToClientId: (Map<UserId, List<ClientId>>) -> Boolean
    ) {
        everySuspend {
            clientRepository.storeMapOfUserToClientId(matches { mapUserToClientId(it) })
        }.returns(result)
    }

    override suspend fun withStoreUserClientListAndRemoveRedundantClients(
        result: Either<StorageFailure, Unit>,
        clients: (List<InsertClientParam>) -> Boolean
    ) {
        everySuspend {
            clientRepository.storeUserClientListAndRemoveRedundantClients(matches { clients(it) })
        }.returns(result)
    }

    override suspend fun withSelfClientsResult(result: Either<NetworkFailure, List<Client>>) {
        everySuspend { clientRepository.selfListOfClients() }.returns(result)
    }
}
