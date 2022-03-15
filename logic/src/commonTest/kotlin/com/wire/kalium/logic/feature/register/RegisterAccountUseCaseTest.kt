package com.wire.kalium.logic.feature.register

import com.wire.kalium.logic.configuration.ServerConfig
import com.wire.kalium.logic.data.register.RegisterAccountRepository
import com.wire.kalium.logic.feature.auth.LoginUseCase
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.configure
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class RegisterAccountUseCaseTest {

    @Mock
    private val registerAccountRepository = configure(mock(classOf<RegisterAccountRepository>())) {
        stubsUnitByDefault = true
    }

    @Mock
    private val loginUseCase = mock(classOf<LoginUseCase>())

    private lateinit var registerAccountUseCase: RegisterAccountUseCase

    @BeforeTest
    fun setup() {
        registerAccountUseCase = RegisterAccountUseCase(registerAccountRepository, loginUseCase)
    }

    @Test
    fun givenValidRegisterAccountResponse_whenRegisteringAccount_thenLoginUseCaseMustBeInvoked() = runTest {
        given(registerAccountRepository)
            .coroutine { registerWithEmail(EMAIL, CODE, NAME, PASSWORD, serverConfig.apiBaseUrl) }
            .then { Either.Right() }

    }

    private companion object {
        val serverConfig =
            ServerConfig("apiBaseUrl", "accountBaseUrl", "webSocketBaseUrl", "blackListUrl", "teamsUrl", "websiteUrl", "title")
        const val EMAIL = "user@domain.de"
        const val CODE = "123456"
        const val PASSWORD = "password"
        const val NAME = "bond 007"
    }
}
