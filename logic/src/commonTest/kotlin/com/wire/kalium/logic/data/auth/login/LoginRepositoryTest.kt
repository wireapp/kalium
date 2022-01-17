package com.wire.kalium.logic.data.auth.login

import com.wire.kalium.network.api.user.login.LoginApi
import com.wire.kalium.network.api.user.login.LoginResponse
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.statement.DefaultHttpResponse
import io.ktor.client.statement.HttpResponse
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test

class LoginRepositoryTest {

    @Mock
    val loginApi = mock(classOf<LoginApi>())

    private lateinit var loginRepository: LoginRepository


    @BeforeTest
    fun setup() {
        loginRepository = LoginRepositoryImpl(loginApi, CLIENT_LABEL)
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
