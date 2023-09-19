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

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.client.ClientType
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.auth.AddAuthenticatedUserUseCase
import com.wire.kalium.logic.feature.auth.AuthenticationResult
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.feature.auth.autoVersioningAuth.AutoVersionAuthScopeUseCase
import com.wire.kalium.logic.feature.client.RegisterClientResult
import com.wire.kalium.logic.feature.client.RegisterClientUseCase
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.network.api.base.authenticated.userDetails.ListUserRequest
import com.wire.kalium.network.api.base.authenticated.userDetails.qualifiedIds
import com.wire.kalium.network.api.base.model.AccessTokenDTO
import com.wire.kalium.network.api.base.model.QualifiedID
import com.wire.kalium.network.api.base.model.SelfUserDTO
import com.wire.kalium.network.tools.ServerConfigDTO
import io.ktor.http.HttpMethod

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test
import util.AccessTokenDTOJson
import util.ClientResponseJson
import util.ErrorResponseJson
import util.ListOfClientsResponseJson
import util.ListUsersResponseJson
import util.LoginWithEmailRequestJson
import util.MockUnboundNetworkClient
import util.MockUnboundNetworkClient.createMockEngine
import util.ServerConfigDTOJson
import util.UserDTOJson
import util.VersionInfoDTOJson

class TempTest {
//     @Test
//     fun testFun() = runTest {
//         val networkClient = createMockEngine(
//             ACME_DIRECTORIES_RESPONSE,
//             statusCode = HttpStatusCode.OK,
//             assertion = {
//                 val contentType = ContentType.Application.Json.withParameter("charset", "UTF-8")
//                 assertTrue(
//                     contentType.match(this.body.contentType ?: ContentType.Any),
//                     "contentType: ${this.body.contentType} doesn't match expected contentType: $contentType"
//                 )
//                 assertContains(this.url.toString(), ACME_DIRECTORIES_PATH)
//                 assertEquals(this.method, method)
//                 assertTrue(this.url.parameters.names().isEmpty())
//             }
//         )
//
//         val coreLogic = coreLogic(
//             rootPath = "$HOME_DIRECTORY/.kalium/accounts",
//             kaliumConfigs = KaliumConfigs(
//                 developmentApiEnabled = true,
//                 encryptProteusStorage = true,
//                 isMLSSupportEnabled = true,
//                 wipeOnDeviceRemoval = true,
//                 useMockEngine = true,
//                 mockEngine = networkClient
//             ),
//         )
//
//         launch {
//             val expected = ACME_DIRECTORIES_SAMPLE
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
    fun logoutTest() = runTest {
        val expectedLoginRequest = MockUnboundNetworkClient.TestRequestHandler(
            path = "https://test.api.com/v1/login?persist=true", //todo: remove hardcoded paths!
            responseBody = VALID_ACCESS_TOKEN_RESPONSE.rawJson, //todo: use the data mocked/sampled in the api test of network module
            statusCode = HttpStatusCode.OK,
            httpMethod = HttpMethod.Post,
            headers = mapOf("set-cookie" to "zuid=$refreshToken")
        )

        val expectedSelfResponse = MockUnboundNetworkClient.TestRequestHandler(
            path = "https://test.api.com/v1/self",
            responseBody = VALID_SELF_RESPONSE.rawJson,
            httpMethod = HttpMethod.Get,
            statusCode = HttpStatusCode.OK,
        )
        val params = ListUserRequest.qualifiedIds(listOf(QualifiedIDSamples.one, QualifiedIDSamples.two))

        val expectedUserDetailsRequest = MockUnboundNetworkClient.TestRequestHandler(
            path = "https://test.api.com/v1/logout",
            responseBody = SUCCESS_RESPONSE.rawJson,
            httpMethod = HttpMethod.Post,
            statusCode = HttpStatusCode.Forbidden,
        )

        val expectedApiVersionRequest = MockUnboundNetworkClient.TestRequestHandler(
            path = "https://test.api.com/api-version",
            responseBody = ServerConfigDTOJson.validServerConfigResponse.rawJson,
            httpMethod = HttpMethod.Get,
            statusCode = HttpStatusCode.OK,
        )

        val registerClientsRequest = MockUnboundNetworkClient.TestRequestHandler(
            path = "https://test.api.com/v1/clients",
            httpMethod = HttpMethod.Post,
            responseBody = ClientResponseJson.valid.rawJson,
            statusCode = HttpStatusCode.OK,
        )
        val getClientsRequest = MockUnboundNetworkClient.TestRequestHandler(
            path = "https://test.api.com/v1/clients",
            httpMethod = HttpMethod.Get,
            responseBody = ListOfClientsResponseJson.valid.rawJson,
            statusCode = HttpStatusCode.OK,
        )
        val accessApiRequest = MockUnboundNetworkClient.TestRequestHandler(
            path = "https://test.api.com/v1/access",
            httpMethod = HttpMethod.Post,
            responseBody = VALID_ACCESS_TOKEN_RESPONSE.rawJson,
            statusCode = HttpStatusCode.OK,
        )
        val networkClient = createMockEngine(
            listOf(
                expectedLoginRequest,
                expectedSelfResponse,
                expectedUserDetailsRequest,
                expectedApiVersionRequest,
                registerClientsRequest,
                accessApiRequest,
                getClientsRequest
            )
        )

        val coreLogic = CoreLogic(
            rootPath = "$HOME_DIRECTORY/.kalium/accounts-test",
            kaliumConfigs = KaliumConfigs(
                developmentApiEnabled = true,
                encryptProteusStorage = true,
                isMLSSupportEnabled = true,
                wipeOnDeviceRemoval = true,
                useMockEngine = true,
                mockEngine = networkClient
            ), "Wire Integration Tests"
        )

        launch {
            val email = "user@domain.com"
            val password = "password"

            val authScope = getAuthScope(coreLogic, MockUnboundNetworkClient.TEST_BACKEND_CONFIG.links)
            //todo: modularized/extract functions from this code, e.g.: login, register device, logout, prepare, before test, after test and...
            //todo: for instance take a look on monkeys app!

            val loginResult = authScope.login(email, password, true)
            if (loginResult !is AuthenticationResult.Success) {
                error("User creds didn't work ($email, $password)")
            }

            coreLogic.globalScope {
                val storeResult = addAuthenticatedAccount(
                    serverConfigId = loginResult.serverConfigId,
                    ssoId = loginResult.ssoID,
                    authTokens = loginResult.authData,
                    proxyCredentials = loginResult.proxyCredentials,
                    replace = true
                )
                if (storeResult !is AddAuthenticatedUserUseCase.Result.Success) {
                    error("Failed to store user. $storeResult")
                }
            }
            val userSession = coreLogic.getSessionScope(loginResult.authData.userId)
            val registerClientParam = RegisterClientUseCase.RegisterClientParam(
                password = password,
                capabilities = emptyList(),
                clientType = ClientType.Temporary
            )
            val registerResult = userSession.client.getOrRegister(registerClientParam)
            if (registerResult is RegisterClientResult.Failure) {
                error("Failed registering client of monkey : $registerResult")
            }

            val x = userSession.client.selfClients()
            println(x.toString())

            userSession.logout.invoke(LogoutReason.SELF_SOFT_LOGOUT)
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
        const val refreshToken = "415a5306-a476-41bc-af36-94ab075fd881"
        val userID = QualifiedID("user_id", "user.domain.io")
        val accessTokenDto = AccessTokenDTO(
            userId = userID.value,
            value = "Nlrhltkj-NgJUjEVevHz8Ilgy_pyWCT2b0kQb-GlnamyswanghN9DcC3an5RUuA7sh1_nC3hv2ZzMRlIhPM7Ag==.v=1.k=1.d=1637254939." +
                    "t=a.l=.u=75ebeb16-a860-4be4-84a7-157654b492cf.c=18401233206926541098",
            expiresIn = 900,
            tokenType = "Bearer"
        )
        val userDTO = SelfUserDTO(
            id = userID,
            name = "user_name_123",
            accentId = 2,
            assets = listOf(),
            deleted = null,
            email = "test@testio.test",
            handle = "mrtestio",
            service = null,
            teamId = null,
            expiresAt = "2026-03-25T14:17:27.364Z",
            nonQualifiedId = "",
            locale = "",
            managedByDTO = null,
            phone = null,
            ssoID = null
        )
        val VALID_ACCESS_TOKEN_RESPONSE = AccessTokenDTOJson.createValid(accessTokenDto)
        val VALID_SELF_RESPONSE = UserDTOJson.createValid(userDTO)

        val LOGIN_WITH_EMAIL_REQUEST = LoginWithEmailRequestJson.validLoginWithEmail
        val ERROR_RESPONSE = ErrorResponseJson.valid.serializableData
        const val QUERY_PERSIST = "persist"
        const val PATH_LOGIN = "/login"
        const val PATH_SELF = "/self"

        /**
         * ACME
         */
        val HOME_DIRECTORY: String = System.getProperty("user.home")

        private const val ACME_DIRECTORIES_PATH = "https://balderdash.hogwash.work:9000/acme/google-android/directory"
        private val SUCCESS_RESPONSE = ListUsersResponseJson.v0

        val ACME_DIRECTORIES_RESPONSE = ACMEApiResponseJsonSample.validAcmeDirectoriesResponse.rawJson
        val ACME_DIRECTORIES_SAMPLE = ACMEApiResponseJsonSample.ACME_DIRECTORIES_SAMPLE

        object QualifiedIDSamples {
            val one = QualifiedID("someValue", "someDomain")
            val two = QualifiedID("anotherValue", "anotherDomain")
            val three = QualifiedID("anotherValue3", "anotherDomain3")
        }
    }
}
