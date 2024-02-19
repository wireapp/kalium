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

import action.ACMEActions
import action.ClientActions
import action.LoginActions
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.feature.auth.autoVersioningAuth.AutoVersionAuthScopeUseCase
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.util.KaliumMockEngine
import com.wire.kalium.network.NetworkState
import com.wire.kalium.network.tools.ServerConfigDTO
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Ignore
import org.junit.Test
import util.MockUnboundNetworkClient
import util.MockUnboundNetworkClient.createMockEngine

class PocIntegrationTest {

    @Ignore("needs to be checked and fix")
    @Test
    fun givenApiWhenGettingACMEDirectoriesThenReturnAsExpectedBasedOnNetworkState() = runTest {
        val mockEngine = createMockEngine(
            listOf(ACMEActions.acmeGetDirectoriesRequestSuccess)
        )

        val coreLogic = createCoreLogic(mockEngine)

        launch {
            TestNetworkStateObserver.DEFAULT_TEST_NETWORK_STATE_OBSERVER.updateNetworkState(NetworkState.NotConnected)

            ACMEActions.acmeDirectoriesErrorNotConnected(
                coreLogic = coreLogic
            )

            TestNetworkStateObserver.DEFAULT_TEST_NETWORK_STATE_OBSERVER.updateNetworkState(NetworkState.ConnectedWithInternet)

            ACMEActions.acmeDirectoriesSuccess(
                coreLogic = coreLogic
            )

            TestNetworkStateObserver.DEFAULT_TEST_NETWORK_STATE_OBSERVER.updateNetworkState(NetworkState.ConnectedWithoutInternet)

            ACMEActions.acmeDirectoriesConnectNoInternet(
                coreLogic = coreLogic
            )
        }
    }

    @Ignore("needs to be checked and fix")
    @Test
    fun givenEmailAndPasswordWhenLoggingInThenRegisterClientAndLogout() = runTest {
        val mockEngine = createMockEngine(
            mutableListOf<MockUnboundNetworkClient.TestRequestHandler>().apply {
                addAll(LoginActions.loginRequestResponseSuccess)
                addAll(ClientActions.clientRequestResponseSuccess)
            }
        )

        val coreLogic = createCoreLogic(mockEngine)

        TestNetworkStateObserver.DEFAULT_TEST_NETWORK_STATE_OBSERVER.updateNetworkState(NetworkState.ConnectedWithInternet)

        launch {
            val authScope = getAuthScope(coreLogic, MockUnboundNetworkClient.TEST_BACKEND_CONFIG.links)

            val loginAuthToken = LoginActions.loginAndAddAuthenticatedUser(
                email = USER_EMAIL,
                password = USER_PASSWORD,
                coreLogic = coreLogic,
                authScope = authScope
            )

            val userSessionScope = ClientActions.registerClient(
                password = USER_PASSWORD,
                userId = loginAuthToken.userId,
                coreLogic = coreLogic
            )

            val x = userSessionScope.client.fetchSelfClients()
            println(x.toString())

            userSessionScope.logout.invoke(LogoutReason.SELF_SOFT_LOGOUT)
        }
    }

    private suspend fun getAuthScope(coreLogic: CoreLogic, backend: ServerConfigDTO.Links): AuthenticationScope {
        val result = coreLogic.versionedAuthenticationScope(
            ServerConfig.Links(
                api = backend.api,
                accounts = backend.accounts,
                webSocket = backend.webSocket,
                blackList = backend.blackList,
                teams = backend.teams,
                website = backend.website,
                title = backend.title,
                isOnPremises = true,
                apiProxy = null
            )
        ).invoke(null)
        if (result !is AutoVersionAuthScopeUseCase.Result.Success) {
            error("Failed getting AuthScope: $result")
        }
        return result.authenticationScope
    }

    companion object {
        private val HOME_DIRECTORY: String = System.getProperty("user.home")
        private val USER_EMAIL = "user@domain.com"
        private val USER_PASSWORD = "password"

        fun createCoreLogic(mockEngine: MockEngine) = CoreLogic(
            rootPath = "$HOME_DIRECTORY/.kalium/accounts-test",
            kaliumConfigs = KaliumConfigs(
                developmentApiEnabled = true,
                encryptProteusStorage = true,
                isMLSSupportEnabled = true,
                wipeOnDeviceRemoval = true,
                kaliumMockEngine = KaliumMockEngine(mockEngine = mockEngine),
                mockNetworkStateObserver = TestNetworkStateObserver.DEFAULT_TEST_NETWORK_STATE_OBSERVER
            ), "Wire Integration Tests"
        )
    }
}
