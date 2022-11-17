package com.wire.kalium.api.v0.user.login

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.TEST_BACKEND
import com.wire.kalium.model.AccessTokenDTOJson
import com.wire.kalium.model.UserDTOJson
import com.wire.kalium.network.api.base.model.AuthenticationResultDTO
import com.wire.kalium.network.api.base.unauthenticated.SSOLoginApi
import com.wire.kalium.network.api.v0.unauthenticated.SSOLoginApiV0
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.protocolWithAuthority
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class SSOLoginApiV0Test : ApiTest {

    @Test
    fun givenBEResponseSuccess_whenCallingInitiateSSOEndpointWithNoRedirect_thenRequestConfiguredCorrectly() = runTest {
        val uuid = "uuid"
        val param = SSOLoginApi.InitiateParam.WithoutRedirect(uuid)
        val expectedPath = "$PATH_SSO_INITIATE/$uuid"
        val networkClient = mockUnauthenticatedNetworkClient(
            "",
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertHead()
                assertNoQueryParams()
                assertPathEqual(expectedPath)
            }
        )
        val ssoApi: SSOLoginApi = SSOLoginApiV0(networkClient)
        val actual = ssoApi.initiate(param)

        assertIs<NetworkResponse.Success<String>>(actual)
        assertEquals(expectedPath, Url(actual.value).encodedPathAndQuery)
        assertEquals("${Url(TEST_BACKEND.links.api).protocolWithAuthority}$expectedPath", actual.value)
    }

    @Test
    fun givenBEResponseSuccess_whenCallingInitiateSSOEndpointWithRedirect_thenRequestConfiguredCorrectly() = runTest {
        val uuid = "uuid"
        val param =
            SSOLoginApi.InitiateParam.WithRedirect(uuid = uuid, success = "wire://success", error = "wire://error")
        val expectedPathAndQuery =
            "$PATH_SSO_INITIATE/$uuid?success_redirect=wire%3A%2F%2Fsuccess&error_redirect=wire%3A%2F%2Ferror"
        val networkClient = mockUnauthenticatedNetworkClient(
            "",
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertHead()
                assertPathAndQueryEqual(expectedPathAndQuery)
            }
        )
        val ssoApi: SSOLoginApi = SSOLoginApiV0(networkClient)
        val actual = ssoApi.initiate(param)

        assertIs<NetworkResponse.Success<String>>(actual)
        assertEquals(expectedPathAndQuery, Url(actual.value).encodedPathAndQuery)
        assertEquals("${Url(TEST_BACKEND.links.api).protocolWithAuthority}$expectedPathAndQuery", actual.value)
    }

    @Test
    fun givenBEResponseSuccess_whenCallingFinalizeSSOEndpointWithRedirect_thenRequestConfiguredCorrectly() = runTest {
        val cookie = "cookie"
        val networkClient = mockUnauthenticatedNetworkClient(
            "",
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertHeaderEqual(HttpHeaders.Cookie, "zuid=$cookie")
                assertPathEqual(PATH_SSO_FINALIZE)
            }
        )
        val ssoApi: SSOLoginApi = SSOLoginApiV0(networkClient)
        val actual = ssoApi.finalize(cookie)

        assertIs<NetworkResponse.Success<String>>(actual)
    }

    @Test
    fun givenBEResponseSuccess_whenFetchingAuthToken_thenTheRefreshTokenIsClean() = runTest {
        val cookie = "zuid=cookie"
        val authResponse = AccessTokenDTOJson.valid
        val selfResponse = UserDTOJson.valid
        val networkClient = mockUnauthenticatedNetworkClient(
            listOf(
                ApiTest.TestRequestHandler(
                    path = PATH_ACCESS,
                    authResponse.rawJson,
                    statusCode = HttpStatusCode.OK,
                    assertion = {
                        assertGet()
                        assertHeaderEqual(HttpHeaders.Cookie, cookie)
                        assertPathEqual(PATH_ACCESS)
                    }
                ),
                ApiTest.TestRequestHandler(
                    path = PATH_SELF,
                    selfResponse.rawJson,
                    statusCode = HttpStatusCode.OK,
                    assertion = {
                        assertGet()
                        assertPathEqual(PATH_SELF)
                    }
                )
            )
        )
        val ssoApi: SSOLoginApi = SSOLoginApiV0(networkClient)
        val actual = ssoApi.provideLoginSession(cookie)

        assertIs<NetworkResponse.Success<AuthenticationResultDTO>>(actual)
        assertEquals(cookie.removePrefix("zuid="), actual.value.sessionDTO.refreshToken)
        assertEquals(authResponse.serializableData.value, actual.value.sessionDTO.accessToken)
        assertEquals(authResponse.serializableData.tokenType, actual.value.sessionDTO.tokenType)
        assertEquals(selfResponse.serializableData.id, actual.value.sessionDTO.userId)
        assertEquals(selfResponse.serializableData, actual.value.userDTO)
    }

    @Test
    fun cookieIsMissingZuidToke_whenFetchingAuthToken_thenReturnError() = runTest {
        val cookie = "cookie"
        val authResponse = AccessTokenDTOJson.valid
        val networkClient = mockUnauthenticatedNetworkClient(
            listOf(
                ApiTest.TestRequestHandler(
                    path = PATH_ACCESS,
                    authResponse.rawJson,
                    statusCode = HttpStatusCode.OK
                )
            )
        )
        val ssoApi: SSOLoginApi = SSOLoginApiV0(networkClient)
        val actual = ssoApi.provideLoginSession(cookie)

        assertIs<NetworkResponse.Error>(actual)
        assertIs<KaliumException.GenericError> (actual.kException)
    }

    private companion object {
        const val PATH_SSO_INITIATE = "/sso/initiate-login"
        const val PATH_SSO_FINALIZE = "/sso/finalize-login"
        const val PATH_ACCESS = "/access"
        const val PATH_SELF = "/self"

    }

}
