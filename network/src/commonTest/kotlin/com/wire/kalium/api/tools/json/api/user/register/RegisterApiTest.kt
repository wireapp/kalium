package com.wire.kalium.api.tools.json.api.user.register

import com.wire.kalium.api.ApiTest
import com.wire.kalium.network.api.user.register.RegisterApi
import com.wire.kalium.network.api.user.register.RegisterApiImpl
import com.wire.kalium.network.api.user.register.RegisterResponse
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RegisterApiTest : ApiTest {

    @Test
    fun givenAValidEmail_whenRegisteringAccountWithEMail_theRequestShouldBeConfiguredCorrectly() = runTest {
        val httpClient = mockUnauthenticatedHttpClient(
            VALID_REGISTER_RESPONSE.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertJson()
                assertNoQueryParams()
                assertPathEqual("/register")
                assertHttps()
                assertHostEqual(TEST_HOST)
                assertBodyContent(VALID_PERSONAL_ACCOUNT_REQUEST.rawJson)
            }
        )
        val registerApi: RegisterApi = RegisterApiImpl(httpClient)
        val result = registerApi.register(VALID_PERSONAL_ACCOUNT_REQUEST.serializableData, TEST_HOST)

        assertIs<NetworkResponse.Success<RegisterResponse>>(result)
        assertEquals(VALID_REGISTER_RESPONSE.serializableData, result.value)
    }


    private companion object {
        val VALID_PERSONAL_ACCOUNT_REQUEST = RegisterAccountJson.validPersonalAccountRegister
        val VALID_REGISTER_RESPONSE = RegisterAccountResponseJson.validRegisterResponse

        const val TEST_HOST = """test-https.wire.com"""

    }
}
