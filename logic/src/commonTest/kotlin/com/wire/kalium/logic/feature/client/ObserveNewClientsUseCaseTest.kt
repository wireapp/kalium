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
package com.wire.kalium.logic.feature.client

import app.cash.turbine.test
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.NewClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.auth.AccountInfo
import com.wire.kalium.logic.feature.user.ObserveValidAccountsUseCase
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveNewClientsUseCaseTest {

    @Test
    fun givenNewClientAndCurrentSessionError_thenNewClientErrorResult() = runTest {
        val (_, observeNewClients) = Arrangement()
            .withoutValidAccounts(listOf(TestUser.SELF to null))
            .withCurrentSession(Either.Left(StorageFailure.DataNotFound))
            .withNewClientManager(listOf(TestClient.CLIENT to TestUser.USER_ID))
            .arrange()

        observeNewClients().test {
            assertEquals(NewClientResult.Error, awaitItem())
            awaitComplete()
        }
    }


    @Test
    fun givenNewClientForOtherUser_whenNoSuchUserInValidAccs_thenNewClientErrorResult() = runTest {
        val (_, observeNewClients) = Arrangement()
            .withoutValidAccounts(listOf(TestUser.SELF to null))
            .withCurrentSession(Either.Right(TEST_ACCOUNT_INFO))
            .withNewClientManager(listOf(TestClient.CLIENT to TestUser.OTHER_USER_ID))
            .arrange()

        observeNewClients().test {
            assertEquals(NewClientResult.Error, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenNewClientForCurrentUser_thenNewClientInCurrentUserResult() = runTest {
        val (arrangement, observeNewClients) = Arrangement()
            .withoutValidAccounts(listOf(TestUser.SELF to null))
            .withCurrentSession(Either.Right(TEST_ACCOUNT_INFO))
            .withNewClientManager(listOf(TestClient.CLIENT to TestUser.USER_ID))
            .arrange()

        observeNewClients().test {
            assertEquals(NewClientResult.InCurrentAccount(listOf(TestClient.CLIENT), TestUser.USER_ID), awaitItem())

            verify(arrangement.observeValidAccounts)
                .suspendFunction(arrangement.observeValidAccounts::invoke)
                .wasNotInvoked()

            awaitComplete()
        }
    }

    @Test
    fun givenNewClientForOtherUser_thenNewClientInOtherUserResult() = runTest {
        val (arrangement, observeNewClients) = Arrangement()
            .withoutValidAccounts(listOf(TestUser.SELF.copy(id = TestUser.OTHER_USER_ID) to null))
            .withCurrentSession(Either.Right(TEST_ACCOUNT_INFO))
            .withNewClientManager(listOf(TestClient.CLIENT to TestUser.OTHER_USER_ID))
            .arrange()

        observeNewClients().test {
            assertEquals(
                NewClientResult.InOtherAccount(
                    listOf(TestClient.CLIENT),
                    TestUser.OTHER_USER_ID,
                    TestUser.SELF.name,
                    TestUser.SELF.handle
                ), awaitItem()
            )

            verify(arrangement.observeValidAccounts)
                .suspendFunction(arrangement.observeValidAccounts::invoke)
                .wasInvoked(exactly = once)

            awaitComplete()
        }
    }

    @Test
    fun givenFewNewClientsForMultipleUsers_thenNewClientInCurrentUserResult() = runTest {
        val client1 = TestClient.CLIENT
        val client2 = TestClient.CLIENT.copy(id = ClientId("other_client"))
        val (arrangement, observeNewClients) = Arrangement()
            .withoutValidAccounts(listOf(TestUser.SELF to null))
            .withCurrentSession(Either.Right(TEST_ACCOUNT_INFO))
            .withNewClientManager(listOf(client1 to TestUser.USER_ID, client2 to TestUser.OTHER_USER_ID))
            .arrange()

        observeNewClients().test {
            assertEquals(NewClientResult.InCurrentAccount(listOf(client1), TestUser.USER_ID), awaitItem())

            verify(arrangement.observeValidAccounts)
                .suspendFunction(arrangement.observeValidAccounts::invoke)
                .wasNotInvoked()

            awaitComplete()
        }
    }

    @Test
    fun givenFewNewClientsForCurrentUsers_thenNewClientInCurrentUserResult() = runTest {
        val client1 = TestClient.CLIENT
        val client2 = TestClient.CLIENT.copy(id = ClientId("other_client"))
        val (arrangement, observeNewClients) = Arrangement()
            .withoutValidAccounts(listOf(TestUser.SELF to null))
            .withCurrentSession(Either.Right(TEST_ACCOUNT_INFO))
            .withNewClientManager(listOf(client1 to TestUser.USER_ID, client2 to TestUser.USER_ID))
            .arrange()

        observeNewClients().test {
            assertEquals(NewClientResult.InCurrentAccount(listOf(client1, client2), TestUser.USER_ID), awaitItem())

            verify(arrangement.observeValidAccounts)
                .suspendFunction(arrangement.observeValidAccounts::invoke)
                .wasNotInvoked()

            awaitComplete()
        }
    }

    companion object {
        val TEST_ACCOUNT_INFO: AccountInfo = AccountInfo.Valid(userId = TestUser.USER_ID)
    }

    private class Arrangement {
        @Mock
        val observeValidAccounts = mock(ObserveValidAccountsUseCase::class)

        @Mock
        val sessionRepository = mock(SessionRepository::class)

        @Mock
        val newClientRepository = mock(NewClientRepository::class)

        init {
            given(observeValidAccounts)
                .suspendFunction(observeValidAccounts::invoke)
                .whenInvoked()
                .thenReturn(flowOf(listOf()))
        }

        private var observeNewClientsUseCase: ObserveNewClientsUseCase =
            ObserveNewClientsUseCaseImpl(sessionRepository, observeValidAccounts, newClientRepository, userSessionScopeProvider.value)

        suspend fun withCurrentSession(result: Either<StorageFailure, AccountInfo>) = apply {
            given(sessionRepository)
                .coroutine { currentSession() }
                .then { result }
        }

        fun withNewClientManager(result: List<Pair<Client, UserId>>) = apply {
            given(newClientRepository)
                .suspendFunction(newClientRepository::observeNewClients)
                .whenInvoked()
                .then { flowOf(Either.Right(result)) }
        }

        fun withoutValidAccounts(result: List<Pair<SelfUser, Team?>>) = apply {
            given(observeValidAccounts)
                .suspendFunction(observeValidAccounts::invoke)
                .whenInvoked()
                .thenReturn(flowOf(result))
        }

        fun arrange() = this to observeNewClientsUseCase
    }
}
