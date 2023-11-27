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
package com.wire.kalium.logic.util.arrangement.repository

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.OtherUserClient
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.mock

internal interface ClientRepositoryArrangement {
    val clientRepository: ClientRepository

    fun withUpdateClientProteusVerificationStatus(result: Either<StorageFailure, Unit>): ClientRepositoryArrangementImpl
    fun withClientsByUserId(result: Either<StorageFailure, List<OtherUserClient>>): ClientRepositoryArrangementImpl
    fun withRemoveClientsAndReturnUsersWithNoClients(result: Either<StorageFailure, List<String>>): ClientRepositoryArrangementImpl
}

internal open class ClientRepositoryArrangementImpl : ClientRepositoryArrangement {
    @Mock
    override val clientRepository: ClientRepository = mock(ClientRepository::class)

    override fun withUpdateClientProteusVerificationStatus(result: Either<StorageFailure, Unit>) = apply {
        given(clientRepository)
            .suspendFunction(clientRepository::updateClientProteusVerificationStatus)
            .whenInvokedWith(any(), any(), any())
            .thenReturn(result)
    }

    override fun withClientsByUserId(result: Either<StorageFailure, List<OtherUserClient>>) = apply {
        given(clientRepository)
            .suspendFunction(clientRepository::getClientsByUserId)
            .whenInvokedWith(any())
            .thenReturn(result)
    }
}
