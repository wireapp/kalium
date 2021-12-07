package com.wire.kalium.api.tools.json.api.user.logout

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.tools.json.api.user.client.RegisterClientRequestJson
import com.wire.kalium.api.tools.json.api.user.client.RegisterClientResponseJson
import com.wire.kalium.api.tools.json.model.ErrorResponseJson
import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.api.user.client.ClientApi
import com.wire.kalium.network.api.user.client.ClientApiImp
import io.ktor.client.call.receive
import io.ktor.client.features.ClientRequestException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


@ExperimentalCoroutinesApi
class LogoutApiTest : ApiTest {
    @Test
    fun givenAValidRegisterLogoutRequest_whenCallingTheRegisterLogoutEndpoint_theRequestShouldBeConfiguredCorrectly() =
        runTest {
            val httpClient = mockHttpClient(
                VALID_REGISTER_CLIENT_RESPONSE.rawJson,
                statusCode = HttpStatusCode.Created,
                assertion = {
                    assertPost()
                    assertJson()
                    assertNoQueryParams()
                    assertPathEqual(PATH_LOGOUT)
                    assertHeaderExist(HttpHeaders.Cookie)
                }
            )
            val clientApi: ClientApi = ClientApiImp(httpClient)
            val response = clientApi.registerClient(REGISTER_CLIENT_REQUEST.serializableData)
            assertEquals(response.resultBody, VALID_REGISTER_CLIENT_RESPONSE.serializableData)
        }

    @Test
    fun givenTheServerReturnsAnError_whenCallingTheLogoutEndpoint_theCorrectExceptionIsThrown() = runTest {
        val httpClient = mockHttpClient(
            ErrorResponseJson.valid.rawJson,
            statusCode = HttpStatusCode.Unauthorized
        )
        val clientApi: ClientApi = ClientApiImp(httpClient)
        val error = assertFailsWith<ClientRequestException> { clientApi.registerClient(REGISTER_CLIENT_REQUEST.serializableData) }
        assertEquals(error.response.receive<ErrorResponse>(), ERROR_RESPONSE)
    }


    private companion object {
        const val PATH_LOGOUT = "access/logout"
        val REGISTER_CLIENT_REQUEST = RegisterClientRequestJson.valid
        val VALID_REGISTER_CLIENT_RESPONSE = RegisterClientResponseJson.valid
        val ERROR_RESPONSE = ErrorResponseJson.valid.serializableData
    }
}
