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
package com.wire.kalium.logic.feature.legalhold

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.DeviceType
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.any
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals

class MembersHavingLegalHoldClientUseCaseTest {
    @Test
    fun givenConversationMemberHasLegalHoldClient_whenCheckingMembersHavingLegalHoldClient_thenReturnListWithThisMemberUserId() = runTest {
        // given
        val userId = UserId("userId", "domain")
        val clients = mapOf(userId to listOf(TestClient.CLIENT.copy(deviceType = DeviceType.LegalHold)))
        val (_, useCase) = Arrangement()
            .withGetClientsOfConversation(Either.Right(clients))
            .arrange()
        // when
        val result = useCase.invoke(ConversationId("conversationId", "domain"))
        // then
        result.shouldSucceed { assertContentEquals(listOf(userId), it) }
    }

    @Test
    fun givenConversationMemberDoesNotHaveLegalHoldClient_whenCheckingMembersHavingLegalHoldClient_thenReturnEmptyList() = runTest {
        // given
        val userId = UserId("userId", "domain")
        val clients = mapOf(userId to listOf(TestClient.CLIENT.copy(deviceType = DeviceType.Phone)))
        val (_, useCase) = Arrangement()
            .withGetClientsOfConversation(Either.Right(clients))
            .arrange()
        // when
        val result = useCase.invoke(ConversationId("conversationId", "domain"))
        // then
        result.shouldSucceed { assertContentEquals(emptyList(), it) }
    }

    @Test
    fun givenFailure_whenCheckingMembersHavingLegalHoldClient_thenReturnFailure() = runTest {
        // given
        val (_, useCase) = Arrangement()
            .withGetClientsOfConversation(Either.Left(StorageFailure.Generic(IOException())))
            .arrange()
        // when
        val result = useCase.invoke(ConversationId("conversationId", "domain"))
        // then
        result.shouldFail()
    }

    internal class Arrangement {
                private val clientRepository = mock(ClientRepository::class)
        private val useCase by lazy { MembersHavingLegalHoldClientUseCaseImpl(clientRepository) }
        fun arrange() = this to useCase
        suspend fun withGetClientsOfConversation(result: Either<StorageFailure, Map<UserId, List<Client>>>) = apply {
            coEvery {
                clientRepository.getClientsByConversationId(any())
            }.returns(result)
        }
    }
}
