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
package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.test_util.TestNetworkResponseError
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.self.SelfApi
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.UserDAO
import io.ktor.http.HttpStatusCode
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okio.IOException
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountRepositoryTest {

    @Test
    fun givenANewDisplayName_whenUpdatingFails_thenShouldNotPersistLocallyTheName() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withUpdateDisplayNameApiRequestResponse(TestNetworkResponseError.genericResponseError())
            .arrange()

        userRepository.updateSelfDisplayName("newDisplayName").shouldFail()

        verify(arrangement.selfApi)
            .suspendFunction(arrangement.selfApi::updateSelf)
            .with(any())
            .wasInvoked(exactly = once)
        verify(arrangement.userDAO)
            .suspendFunction(arrangement.userDAO::updateUserDisplayName)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenANewDisplayName_whenUpdatingOk_thenShouldSucceedAndPersistTheNameLocally() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withUpdateDisplayNameApiRequestResponse(NetworkResponse.Success(Unit, mapOf(), HttpStatusCode.OK.value))
            .arrange()

        userRepository.updateSelfDisplayName("newDisplayName").shouldSucceed()

        verify(arrangement.selfApi)
            .suspendFunction(arrangement.selfApi::updateSelf)
            .with(any())
            .wasInvoked(exactly = once)
        verify(arrangement.userDAO)
            .suspendFunction(arrangement.userDAO::updateUserDisplayName)
            .with(any(), any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenUpdateEmailSuccess_whenChangingEmail_thenSuccessIsReturned() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withRemoteUpdateEmail(NetworkResponse.Success(true, mapOf(), 200))
            .arrange()

        userRepository.updateSelfEmail("newEmail").shouldSucceed()

        verify(arrangement.selfApi)
            .suspendFunction(arrangement.selfApi::updateEmailAddress)
            .with(eq("newEmail"))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenUpdateEmailFailure_whenChangingEmail_thenFailureIsReturned() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withRemoteUpdateEmail(NetworkResponse.Error(KaliumException.GenericError(IOException())))
            .arrange()

        val result = userRepository.updateSelfEmail("newEmail")

        with(result) {
            shouldFail()
            verify(arrangement.selfApi)
                .suspendFunction(arrangement.selfApi::updateEmailAddress)
                .with(eq("newEmail"))
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenRequestToDeleteAccount_thenCallTheCorrectAPi() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withDeleteAccountRequest()
            .withDeleteAccountRequest(NetworkResponse.Success(Unit, mapOf(), 200))
            .arrange()

        userRepository.deleteAccount(null).shouldSucceed()

        verify(arrangement.selfApi)
            .suspendFunction(arrangement.selfApi::deleteAccount)
            .with(anything())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenError_whenCallTheDeleteAccountAPi_thenErrorIsPropagated() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withDeleteAccountRequest()
            .withDeleteAccountRequest(NetworkResponse.Error(KaliumException.GenericError(IOException())))
            .arrange()

        userRepository.deleteAccount(null).shouldFail()

        verify(arrangement.selfApi)
            .suspendFunction(arrangement.selfApi::deleteAccount)
            .with(anything())
            .wasInvoked(exactly = once)
    }


    private class Arrangement {
        @Mock
        val userDAO = configure(mock(UserDAO::class)) { stubsUnitByDefault = true }

        @Mock
        val selfApi = mock(SelfApi::class)

        val selfUserId = TestUser.SELF.id

        val accountRepo: AccountRepository by lazy {
            AccountRepositoryImpl(
                userDAO = userDAO,
                selfUserId = selfUserId,
                selfApi = selfApi
            )
        }

        fun withUpdateDisplayNameApiRequestResponse(response: NetworkResponse<Unit>) = apply {
            given(selfApi)
                .suspendFunction(selfApi::updateSelf)
                .whenInvokedWith(any())
                .thenReturn(response)
        }

        fun withRemoteUpdateEmail(result: NetworkResponse<Boolean>) = apply {
            given(selfApi)
                .suspendFunction(selfApi::updateEmailAddress)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withDeleteAccountRequest(result: NetworkResponse<Unit> = NetworkResponse.Success(Unit, mapOf(), 200)) = apply {
            given(selfApi)
                .suspendFunction(selfApi::deleteAccount)
                .whenInvokedWith(anything())
                .thenReturn(result)
        }

        fun arrange() = this to accountRepo
    }
}
