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
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import okio.IOException

class AccountRepositoryTest {

    @Test
    fun givenANewDisplayName_whenUpdatingFails_thenShouldNotPersistLocallyTheName() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withUpdateDisplayNameApiRequestResponse(TestNetworkResponseError.genericResponseError())
            .arrange()

        userRepository.updateSelfDisplayName("newDisplayName").shouldFail()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.selfApi.updateSelf(any())
        }
        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.userDAO.updateUserDisplayName(any(), any())
        }
    }

    @Test
    fun givenANewDisplayName_whenUpdatingOk_thenShouldSucceedAndPersistTheNameLocally() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withUpdateDisplayNameApiRequestResponse(NetworkResponse.Success(Unit, mapOf(), HttpStatusCode.OK.value))
            .arrange()

        userRepository.updateSelfDisplayName("newDisplayName").shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.selfApi.updateSelf(any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userDAO.updateUserDisplayName(any(), any())
        }
    }

    @Test
    fun givenUpdateEmailSuccess_whenChangingEmail_thenSuccessIsReturned() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withRemoteUpdateEmail(NetworkResponse.Success(true, mapOf(), 200))
            .arrange()

        userRepository.updateSelfEmail("newEmail").shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.selfApi.updateEmailAddress(eq("newEmail"))
        }
    }

    @Test
    fun givenUpdateEmailFailure_whenChangingEmail_thenFailureIsReturned() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withRemoteUpdateEmail(NetworkResponse.Error(KaliumException.GenericError(IOException())))
            .arrange()

        val result = userRepository.updateSelfEmail("newEmail")

        with(result) {
            shouldFail()
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.selfApi.updateEmailAddress(eq("newEmail"))
            }
        }
    }

    @Test
    fun givenRequestToDeleteAccount_thenCallTheCorrectAPi() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withDeleteAccountRequest()
            .withDeleteAccountRequest(NetworkResponse.Success(Unit, mapOf(), 200))
            .arrange()

        userRepository.deleteAccount(null).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.selfApi.deleteAccount(any())
        }
    }

    @Test
    fun givenError_whenCallTheDeleteAccountAPi_thenErrorIsPropagated() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withDeleteAccountRequest()
            .withDeleteAccountRequest(NetworkResponse.Error(KaliumException.GenericError(IOException())))
            .arrange()

        userRepository.deleteAccount(null).shouldFail()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.selfApi.deleteAccount(any())
        }
    }

    @Test
    fun givenANewAccent_whenUpdatingFails_thenShouldNotPersistLocallyTheAccent() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withUpdateAccentApiRequestResponse(TestNetworkResponseError.genericResponseError())
            .arrange()

        userRepository.updateSelfAccentColor(5).shouldFail()

        verifySuspend(VerifyMode.exactly(1)) { arrangement.selfApi.updateSelf(any()) }
        verifySuspend(VerifyMode.exactly(0)) { arrangement.userDAO.updateUserAccentColor(any(), any()) }
    }

    @Test
    fun givenANewAccent_whenUpdatingOk_thenShouldSucceedAndPersistTheAccentLocally() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withUpdateAccentApiRequestResponse(NetworkResponse.Success(Unit, mapOf(), 200))
            .arrange()

        userRepository.updateSelfAccentColor(7).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) { arrangement.selfApi.updateSelf(any()) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.userDAO.updateUserAccentColor(any(), eq(7)) }
    }

    private class Arrangement {

        val userDAO = mock<UserDAO>(mode = MockMode.autoUnit)
        val selfApi = mock<SelfApi>(mode = MockMode.autoUnit)

        val selfUserId = TestUser.SELF.id

        val accountRepo: AccountRepository by lazy {
            AccountRepositoryImpl(
                userDAO = userDAO,
                selfUserId = selfUserId,
                selfApi = selfApi
            )
        }

        suspend fun withUpdateDisplayNameApiRequestResponse(response: NetworkResponse<Unit>) = apply {
            everySuspend {
                selfApi.updateSelf(any())
            }.returns(response)
        }

        suspend fun withRemoteUpdateEmail(result: NetworkResponse<Boolean>) = apply {
            everySuspend {
                selfApi.updateEmailAddress(any())
            }.returns(result)
        }

        suspend fun withDeleteAccountRequest(result: NetworkResponse<Unit> = NetworkResponse.Success(Unit, mapOf(), 200)) = apply {
            everySuspend {
                selfApi.deleteAccount(any())
            }.returns(result)
        }

        suspend fun withUpdateAccentApiRequestResponse(response: NetworkResponse<Unit>) = apply {
            everySuspend { selfApi.updateSelf(any()) }.returns(response)
        }

        fun arrange() = this to accountRepo
    }
}
