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
package com.wire.kalium.logic.feature.client

import app.cash.turbine.test
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.auth.AccountInfo
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.UserClientRepositoryProvider
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.feature.user.ObserveValidAccountsUseCase
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveNewClientsUseCaseTest {

    @Test
    fun givenNewClientAndCurrentSessionError_thenNewClientErrorResult() = runTest {
        val (_, observeNewClients) = Arrangement()
            .withValidAccounts(listOf(TestUser.SELF to null))
            .withCurrentSession(Either.Left(StorageFailure.DataNotFound))
            .withNewClientsForUser1(listOf(TestClient.CLIENT))
            .arrange()

        observeNewClients().test {
            assertEquals(NewClientResult.Error, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenNewClientForCurrentUser_thenNewClientInCurrentUserResult() = runTest {
        val (_, observeNewClients) = Arrangement()
            .withValidAccounts(listOf(TestUser.SELF to null))
            .withCurrentSession(Either.Right(TEST_ACCOUNT_INFO))
            .withNewClientsForUser1(listOf(TestClient.CLIENT))
            .arrange()

        observeNewClients().test {
            assertEquals(NewClientResult.InCurrentAccount(listOf(TestClient.CLIENT), TestUser.USER_ID), awaitItem())

            awaitComplete()
        }
    }

    @Test
    fun givenNewClientForOtherUser_thenNewClientInOtherUserResult() = runTest {
        val (arrangement, observeNewClients) = Arrangement()
            .withValidAccounts(listOf(TestUser.SELF to null, TestUser.SELF.copy(id = TestUser.OTHER_USER_ID) to null))
            .withCurrentSession(Either.Right(TEST_ACCOUNT_INFO))
            .withNewClientsForUser1(listOf())
            .withNewClientsForUser2(listOf(TestClient.CLIENT))
            .arrange()

        observeNewClients().test {
            assertEquals(
                NewClientResult.InOtherAccount(
                    listOf(TestClient.CLIENT),
                    TestUser.OTHER_USER_ID,
                    TestUser.SELF.name,
                    TestUser.SELF.handle
                ),
                awaitItem()
            )

            coVerify {
                arrangement.observeValidAccounts.invoke()
            }.wasInvoked(exactly = once)

            awaitComplete()
        }
    }

    @Test
    fun givenFewNewClientsForMultipleUsers_thenNewClientInCurrentUserResult() = runTest {
        val client1 = TestClient.CLIENT
        val client2 = TestClient.CLIENT.copy(id = ClientId("other_client"))
        val (_, observeNewClients) = Arrangement()
            .withValidAccounts(listOf(TestUser.SELF to null, TestUser.SELF.copy(id = TestUser.OTHER_USER_ID) to null))
            .withCurrentSession(Either.Right(TEST_ACCOUNT_INFO))
            .withNewClientsForUser1(listOf(client1))
            .withNewClientsForUser2(listOf(client2))
            .arrange()

        observeNewClients().test {
            assertEquals(NewClientResult.InCurrentAccount(listOf(client1), TestUser.USER_ID), awaitItem())

            awaitComplete()
        }
    }

    @Test
    fun givenFewNewClientsForCurrentUsers_thenNewClientInCurrentUserResult() = runTest {
        val client1 = TestClient.CLIENT
        val client2 = TestClient.CLIENT.copy(id = ClientId("other_client"))
        val (_, observeNewClients) = Arrangement()
            .withValidAccounts(listOf(TestUser.SELF to null))
            .withCurrentSession(Either.Right(TEST_ACCOUNT_INFO))
            .withNewClientsForUser1(listOf(client1, client2))
            .arrange()

        observeNewClients().test {
            assertEquals(NewClientResult.InCurrentAccount(listOf(client1, client2), TestUser.USER_ID), awaitItem())

            awaitComplete()
        }
    }

    @Test
    fun givenNoNewClients_thenEmptyResult() = runTest {
        val (_, observeNewClients) = Arrangement()
            .withValidAccounts(listOf(TestUser.SELF to null))
            .withCurrentSession(Either.Right(TEST_ACCOUNT_INFO))
            .withNewClientsForUser1(emptyList())
            .arrange()

        observeNewClients().test {
            assertEquals(NewClientResult.Empty, awaitItem())

            awaitComplete()
        }
    }

    @Test
    fun givenNoAccountsLoggedIn_thenEmptyResult() = runTest {
        val (_, observeNewClients) = Arrangement()
            .withValidAccounts(emptyList())
            .arrange()

        observeNewClients().test {
            assertEquals(NewClientResult.Empty, awaitItem())

            awaitComplete()
        }
    }

    @Test
    fun givenNewClientForCurrentUser_whenUserIsBeingLoggedOut_thenChangeToEmptyResult() = runTest {
        val validAccountsFlow = MutableStateFlow(listOf(TestUser.SELF to null))
        val (_, observeNewClients) = Arrangement()
            .withValidAccountsFlow(validAccountsFlow)
            .withCurrentSession(Either.Right(TEST_ACCOUNT_INFO))
            .withNewClientsForUser1(listOf(TestClient.CLIENT))
            .arrange()

        observeNewClients().test {
            assertEquals(NewClientResult.InCurrentAccount(listOf(TestClient.CLIENT), TestUser.USER_ID), awaitItem())

            validAccountsFlow.value = emptyList()
            advanceUntilIdle()
            assertEquals(NewClientResult.Empty, awaitItem())

            cancel()
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
        val clientRepository1 = mock(ClientRepository::class)

        @Mock
        val clientRepository2 = mock(ClientRepository::class)

        @Mock
        val userClientRepositoryProvider = mock(UserClientRepositoryProvider::class)

        init {
            runBlocking {
                coEvery {
                    observeValidAccounts.invoke()
                }.returns(flowOf(listOf()))

                every {

                    userClientRepositoryProvider.provide(eq(TestUser.USER_ID))

                }.returns(clientRepository1)

                every {

                    userClientRepositoryProvider.provide(eq(TestUser.OTHER_USER_ID))

                }.returns(clientRepository2)
            }
        }

        private var observeNewClientsUseCase: ObserveNewClientsUseCase =
            ObserveNewClientsUseCaseImpl(sessionRepository, observeValidAccounts, userClientRepositoryProvider)

        suspend fun withCurrentSession(result: Either<StorageFailure, AccountInfo>) = apply {
            coEvery {
                sessionRepository.currentSession()
            }.returns(result)
        }

        suspend fun withNewClientsForUser1(result: List<Client>) = apply {
            coEvery {
                clientRepository1.observeNewClients()
            }.returns(flowOf(Either.Right(result)))
        }

        suspend fun withNewClientsForUser2(result: List<Client>) = apply {
            coEvery {
                clientRepository2.observeNewClients()
            }.returns(flowOf(Either.Right(result)))
        }

        suspend fun withValidAccounts(result: List<Pair<SelfUser, Team?>>) = apply {
            coEvery {
                observeValidAccounts.invoke()
            }.returns(flowOf(result))
        }

        suspend fun withValidAccountsFlow(flowResult: Flow<List<Pair<SelfUser, Team?>>>) = apply {
            coEvery {
                observeValidAccounts.invoke()
            }.returns(flowResult)
        }

        fun arrange() = this to observeNewClientsUseCase
    }
}
