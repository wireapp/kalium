package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.configuration.ServerConfig
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.functional.Either
import io.mockative.ConfigurationApi
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.configure
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SaveSessionUseCaseTest {

    @OptIn(ConfigurationApi::class)
    @Mock
    val sessionRepository = configure(mock(classOf<SessionRepository>())) { stubsUnitByDefault = true }
    lateinit var saveSessionUseCase: SaveSessionUseCase

    @BeforeTest
    fun setup() {
        saveSessionUseCase = SaveSessionUseCase(sessionRepository)
    }

    @Test
    fun givenAuthSession_whenSaveSessionUseCaseIsInvoked_thenStoreSessionAndUpdateCurrentSessionAreCalled() = runTest {
        given(sessionRepository).invocation { storeSession(TEST_AUTH_SESSION) }.then { Either.Right(Unit) }

        saveSessionUseCase(TEST_AUTH_SESSION)
        verify(sessionRepository).invocation { storeSession(TEST_AUTH_SESSION) }.wasInvoked(exactly = once)
    }

    private companion object {
        val TEST_SERVER_CONFIG: ServerConfig = ServerConfig(
            id = "config-1",
            apiBaseUrl = "apiBaseUrl.com",
            accountsBaseUrl = "accountsUrl.com",
            webSocketBaseUrl = "webSocketUrl.com",
            blackListUrl = "blackListUrl.com",
            teamsUrl = "teamsUrl.com",
            websiteUrl = "websiteUrl.com",
            title = "Test Title",
            androidSenderId = "123456")
        val TEST_AUTH_SESSION =
            AuthSession(
                userId = UserId("user_id", "domain.de"),
                accessToken = "access_token",
                refreshToken = "refresh_token",
                tokenType = "token_type",
                TEST_SERVER_CONFIG
            )
    }
}
