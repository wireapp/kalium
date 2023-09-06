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

import com.wire.kalium.logic.configuration.server.ApiVersionMapper
import com.wire.kalium.logic.configuration.server.ServerConfigMapper
import com.wire.kalium.logic.configuration.server.ServerConfigMapperImpl
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.network.NetworkState
import com.wire.kalium.network.api.base.model.AccessTokenDTO
import com.wire.kalium.network.api.base.model.QualifiedID
import com.wire.kalium.network.api.base.model.SelfUserDTO
import com.wire.kalium.network.api.base.unbound.acme.AcmeDirectoriesResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.tools.ApiVersionDTO
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import util.AccessTokenDTOJson
import util.ErrorResponseJson
import util.LoginWithEmailRequestJson
import util.MockUnboundNetworkClient
import util.UserDTOJson
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TempTest {
    @Test
    fun testFun() = runTest {
        val networkClient = MockUnboundNetworkClient.mockUnboundNetworkClient(
            ACME_DIRECTORIES_RESPONSE,
            statusCode = HttpStatusCode.OK,
            assertion = {
                val contentType = ContentType.Application.Json.withParameter("charset", "UTF-8")
                assertTrue(
                    contentType.match(this.body.contentType ?: ContentType.Any),
                    "contentType: ${this.body.contentType} doesn't match expected contentType: $contentType"
                )
                assertContains(this.url.toString(), ACME_DIRECTORIES_PATH)
                assertEquals(this.method, method)
                assertTrue(this.url.parameters.names().isEmpty())
            }
        )

        val coreLogic = coreLogic(
            rootPath = "$HOME_DIRECTORY/.kalium/accounts",
            kaliumConfigs = KaliumConfigs(
                developmentApiEnabled = true,
                encryptProteusStorage = true,
                isMLSSupportEnabled = true,
                wipeOnDeviceRemoval = true,
            ),
            networkClient = networkClient
        )

        launch {
            val expected = ACME_DIRECTORIES_SAMPLE

            TestNetworkStateObserver.DEFAULT_TEST_NETWORK_STATE_OBSERVER.updateNetworkState(NetworkState.NotConnected)

            coreLogic.getGlobalScope().unboundNetworkContainer
                .value.acmeApi.getACMEDirectories().also { actual ->
                    assertIs<NetworkResponse.Error>(actual)
                    assertIs<KaliumException.NoNetwork>(actual.kException.cause)
                }

            TestNetworkStateObserver.DEFAULT_TEST_NETWORK_STATE_OBSERVER.updateNetworkState(NetworkState.ConnectedWithInternet)

            coreLogic.getGlobalScope().unboundNetworkContainer
                .value.acmeApi.getACMEDirectories().also { actual ->
                    assertIs<NetworkResponse.Success<AcmeDirectoriesResponse>>(actual)
                    assertEquals(expected, actual.value)
                }

            TestNetworkStateObserver.DEFAULT_TEST_NETWORK_STATE_OBSERVER.updateNetworkState(NetworkState.ConnectedWithoutInternet)

            coreLogic.getGlobalScope().unboundNetworkContainer
                .value.acmeApi.getACMEDirectories().also { actual ->
                    assertIs<NetworkResponse.Error>(actual)
                    assertIs<KaliumException.NoNetwork>(actual.kException.cause)
                }
        }
    }

    @Test
    fun logoutTest() = runTest {
        val networkClient = MockUnboundNetworkClient.mockUnauthenticatedNetworkClient(
            // path = PATH_LOGIN,
            responseBody = VALID_ACCESS_TOKEN_RESPONSE.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
//                 assertPost()
//                 assertQueryExist(QUERY_PERSIST)
//                 assertHttps()
//                 assertJson()
                   // val verificationCode = this.body.toJsonElement().jsonObject["verification_code"]?.jsonPrimitive?.content
                // assertEquals(LOGIN_WITH_EMAIL_REQUEST.serializableData.verificationCode, verificationCode)
            },
            headers = mapOf("set-cookie" to "zuid=$refreshToken")
        )

        val coreLogic = coreLogic(
            rootPath = "$HOME_DIRECTORY/.kalium/accounts",
            kaliumConfigs = KaliumConfigs(
                developmentApiEnabled = true,
                encryptProteusStorage = true,
                isMLSSupportEnabled = true,
                wipeOnDeviceRemoval = true,
            ),
            networkClient = networkClient
        )

        launch {
            val email = "user@domain.com"
            val password = "password"

            val result = coreLogic.getAuthenticationScope(
                serverConfig = MapperProvider.serverConfigMapper().fromDTO(
                    MockUnboundNetworkClient.TEST_BACKEND_CONFIG
                )
            ).login(email, password, false)

            val x = result.toString()
        }
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
            email = null,
            handle = null,
            service = null,
            teamId = null,
            expiresAt = "",
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
        val HOME_DIRECTORY: String = homeDirectory()

        private const val ACME_DIRECTORIES_PATH = "https://balderdash.hogwash.work:9000/acme/google-android/directory"

        val ACME_DIRECTORIES_RESPONSE = ACMEApiResponseJsonSample.validAcmeDirectoriesResponse.rawJson
        val ACME_DIRECTORIES_SAMPLE = ACMEApiResponseJsonSample.ACME_DIRECTORIES_SAMPLE
    }
}
