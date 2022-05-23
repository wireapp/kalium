package com.wire.kalium.api.tools.json.api.user.login

import com.wire.kalium.api.ApiTest
import com.wire.kalium.network.api.user.login.SSOLoginApi
import com.wire.kalium.network.api.user.login.SSOLoginApiImpl
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class SSOLoginApiTest : ApiTest {

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
        val ssoApi: SSOLoginApi = SSOLoginApiImpl(networkClient)
        val actual = ssoApi.initiate(param)

        assertIs<NetworkResponse.Success<String>>(actual)
        TODO()
        //assertEquals("${TEST_HOST.protocolWithAuthority}$expectedPath", actual.value)
    }

    @Test
    fun givenBEResponseSuccess_whenCallingInitiateSSOEndpointWithRedirect_thenRequestConfiguredCorrectly() = runTest {
        val uuid = "uuid"
        val param = SSOLoginApi.InitiateParam.WithRedirect(uuid = uuid, success = "wire://success", error = "wire://error")
        val expectedPathAndQuery = "$PATH_SSO_INITIATE/$uuid?success_redirect=wire%3A%2F%2Fsuccess&error_redirect=wire%3A%2F%2Ferror"
        val networkClient = mockUnauthenticatedNetworkClient(
            "",
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertHead()
                assertPathAndQueryEqual(expectedPathAndQuery)
            }
        )
        val ssoApi: SSOLoginApi = SSOLoginApiImpl(networkClient)
        val actual = ssoApi.initiate(param)

        assertIs<NetworkResponse.Success<String>>(actual)
        TODO()
        //assertEquals("${TEST_HOST.protocolWithAuthority}$expectedPathAndQuery", actual.value)
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
        val ssoApi: SSOLoginApi = SSOLoginApiImpl(networkClient)
        val actual = ssoApi.finalize(cookie)

        assertIs<NetworkResponse.Success<String>>(actual)
    }


    private companion object {
        const val PATH_SSO_INITIATE = "/sso/initiate-login"
        const val PATH_SSO_FINALIZE = "/sso/finalize-login"
    }

}
