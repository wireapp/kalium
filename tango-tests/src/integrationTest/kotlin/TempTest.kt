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

import action.ClientActions
import action.LoginActions
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.feature.auth.autoVersioningAuth.AutoVersionAuthScopeUseCase
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.network.tools.ServerConfigDTO
import data.ResponseData
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test
import util.MockUnboundNetworkClient
import util.MockUnboundNetworkClient.createMockEngine

class TempTest {

//     @Test
//     fun givenApiWhenGettingACMEDirectoriesThenReturnAsExpectedBasedOnNetworkState() = runTest {
//         val mockEngine = createMockEngine(
//             listOf(ResponseData.acmeGetDirectoriesRequestSuccess)
//         )
//
//         val coreLogic = createCoreLogic(mockEngine)
//
//         launch {
//             val expected = ResponseData.ACME_DIRECTORIES_SAMPLE
//
//             TestNetworkStateObserver.DEFAULT_TEST_NETWORK_STATE_OBSERVER.updateNetworkState(NetworkState.NotConnected)
//
//             coreLogic.getGlobalScope().unboundNetworkContainer
//                 .value.acmeApi.getACMEDirectories().also { actual ->
//                     assertIs<NetworkResponse.Error>(actual)
//                     assertIs<KaliumException.NoNetwork>(actual.kException.cause)
//                 }
//
//             TestNetworkStateObserver.DEFAULT_TEST_NETWORK_STATE_OBSERVER.updateNetworkState(NetworkState.ConnectedWithInternet)
//
//             coreLogic.getGlobalScope().unboundNetworkContainer
//                 .value.acmeApi.getACMEDirectories().also { actual ->
//                     assertIs<NetworkResponse.Success<AcmeDirectoriesResponse>>(actual)
//                     assertEquals(expected, actual.value)
//                 }
//
//             TestNetworkStateObserver.DEFAULT_TEST_NETWORK_STATE_OBSERVER.updateNetworkState(NetworkState.ConnectedWithoutInternet)
//
//             coreLogic.getGlobalScope().unboundNetworkContainer
//                 .value.acmeApi.getACMEDirectories().also { actual ->
//                     assertIs<NetworkResponse.Error>(actual)
//                     assertIs<KaliumException.NoNetwork>(actual.kException.cause)
//                 }
//         }
//     }

    @Test
    fun givenEmailAndPasswordWhenLoggingInThenRegisterClientAndLogout() = runTest {
        val mockEngine = createMockEngine(
            listOf(
                ResponseData.loginRequestSuccess,
                ResponseData.selfRequestSuccess,
                ResponseData.userDetailsRequestSuccess,
                ResponseData.apiVersionRequestSuccess,
                ResponseData.registerClientsRequestSuccess,
                ResponseData.accessApiRequestSuccess,
                ResponseData.getClientsRequestSuccess
            )
        )

        val coreLogic = createCoreLogic(mockEngine)

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

            val x = userSessionScope.client.selfClients()
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
        ).invoke()
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
                mockEngine = mockEngine
            ), "Wire Integration Tests"
        )
    }
}
