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

package com.wire.kalium.logic.data.auth.login

import com.wire.kalium.logic.util.stubs.newTestServer
import com.wire.kalium.network.api.base.unauthenticated.LoginApi
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test

class LoginRepositoryTest {

    @Mock
    val loginApi = mock(classOf<LoginApi>())

    val testServer = newTestServer(1)

    private lateinit var loginRepository: LoginRepository


    @BeforeTest
    fun setup() {
        loginRepository = LoginRepositoryImpl(loginApi)
    }

    @Test
    fun givenALoginRepository_WhenCallingLoginWithEmail_ThenTheLoginApiMustBeCalledWithCorrectParam() = runTest {
        // TODO: find a way to mock any api class response
        /*
        given(loginApi).coroutine {
            login(
                LoginApi.LoginParam.LoginWithEmail(
                    email = TEST_EMAIL,
                    password = TEST_PASSWORD,
                    label = CLIENT_LABEL
                ), TEST_PERSIST_CLIENT
            )
        }.then { NetworkResponse.Success(value = LoginResponse("", 123, "", ""), response = Any() ) }

        loginRepository.loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT)
        verify(loginApi).coroutine {
            login(LoginApi.LoginParam.LoginWithEmail(TEST_EMAIL, TEST_PASSWORD, CLIENT_LABEL), TEST_PERSIST_CLIENT)
        }.wasInvoked(exactly = once)
        */
    }

    private companion object {
        const val CLIENT_LABEL = "test_label"
        const val TEST_EMAIL = "email@fu-berlin.de"
        const val TEST_HANDEL = "cool_user"
        const val TEST_PASSWORD = "123456"
        val TEST_PERSIST_CLIENT = Random.nextBoolean()
    }
}
