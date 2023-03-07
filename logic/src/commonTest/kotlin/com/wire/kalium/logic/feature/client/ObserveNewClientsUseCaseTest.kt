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
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.feature.auth.AccountInfo
import com.wire.kalium.logic.feature.user.GetSelfUserUseCase
import com.wire.kalium.logic.feature.user.UserScope
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
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
    fun givenNewClientForCurrentUser_thenNewClientInCurrentUserResult() = runTest {
        val (arrangement, observeNewClients) = Arrangement()
            .withSelfUser(TestUser.SELF)
            .withCurrentSession(Either.Right(TEST_ACCOUNT_INFO))
            .withNewClientManager(TestClient.CLIENT to TestUser.USER_ID)
            .arrange()

        observeNewClients().test {
            assertEquals(NewClientResult.InCurrentAccount(TestClient.CLIENT), awaitItem())

            verify(arrangement.getSelfUserUseCase)
                .suspendFunction(arrangement.getSelfUserUseCase::invoke)
                .wasNotInvoked()
        }
    }

    companion object {
        val TEST_ACCOUNT_INFO: AccountInfo = AccountInfo.Valid(userId = TestUser.USER_ID)
    }

    private class Arrangement {
        @Mock
        private val userSessionScopeProvider = mock(UserSessionScopeProvider::class)

        @Mock
        private val userScope = mock(UserScope::class)

        @Mock
        val sessionRepository = mock(SessionRepository::class)

        @Mock
        val getSelfUserUseCase = mock(GetSelfUserUseCase::class)

        @Mock
        private val userSessionScope = mock(UserSessionScope::class)

        @Mock
        val newClientManager = mock(NewClientManager::class)

        init {
            given(userScope)
                .getter(userScope::getSelfUser)
                .whenInvoked()
                .thenReturn(getSelfUserUseCase)
            given(userSessionScope)
                .getter(userSessionScope::users)
                .whenInvoked()
                .thenReturn(userScope)
            given(userSessionScopeProvider)
                .function(userSessionScopeProvider::get)
                .whenInvokedWith(any())
                .thenReturn(userSessionScope)
        }

        private var observeNewClientsUseCase: ObserveNewClientsUseCase =
            ObserveNewClientsUseCaseImpl(sessionRepository, userSessionScopeProvider, newClientManager)

        fun withSelfUser(result: SelfUser) = apply {
            given(getSelfUserUseCase)
                .suspendFunction(getSelfUserUseCase::invoke)
                .whenInvoked()
                .then { flowOf(result) }
        }

        suspend fun withCurrentSession(result: Either<StorageFailure, AccountInfo>) = apply {
            given(sessionRepository)
                .coroutine { currentSession() }
                .then { result }
        }

        fun withNewClientManager(result: Pair<Client, UserId>) = apply {
            given(newClientManager)
                .function(newClientManager::observeNewClients)
                .whenInvokedWith()
                .then { flowOf(result) }
        }

        fun arrange() = this to observeNewClientsUseCase
    }
}
