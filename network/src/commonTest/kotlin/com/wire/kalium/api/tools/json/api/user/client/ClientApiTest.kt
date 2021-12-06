package com.wire.kalium.api.tools.json.api.user.client

import com.wire.kalium.api.ApiTest
import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.api.prekey.PreKey
import com.wire.kalium.network.api.user.client.Capabilities
import com.wire.kalium.network.api.user.client.ClientApi
import com.wire.kalium.network.api.user.client.ClientApiImp
import com.wire.kalium.network.api.user.client.ClientType
import com.wire.kalium.network.api.user.client.DeviceType
import com.wire.kalium.network.api.user.client.LocationResponse
import com.wire.kalium.network.api.user.client.RegisterClientRequest
import com.wire.kalium.network.api.user.client.RegisterClientResponse
import com.wire.kalium.network.tools.KtxSerializer
import io.ktor.client.call.receive
import io.ktor.client.features.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


class ClientApiTest : ApiTest {
    @Test
    fun givenAValidRegisterClientRequest_whenCallingTheRegisterClientEndpoint_theRequestShouldBeConfiguredCorrectly() =
        runTest {
            val httpClient = mockHttpClient(
                KtxSerializer.json.encodeToString(VALID_Register_Client_RESPONSE),
                statusCode = HttpStatusCode.Created,
                assertion = {
                    assertPost()
                    assertJson()
                    assertNoQueryParams()
                    assertPathEqual(PATH_CLIENTS)
                }
            )
            val clientApi: ClientApi = ClientApiImp(httpClient)
            val response = clientApi.registerClient(Register_Client_REQUEST)
            assertEquals(response.resultBody, VALID_Register_Client_RESPONSE)
        }

    @Test
    fun givenAnInvalidLoginRequest_whenCallingTheLoginEndpoint_theCorrectExceptionIsThrown() = runTest {
        val httpClient = mockHttpClient(
            KtxSerializer.json.encodeToString(ERROR_RESPONSE),
            statusCode = HttpStatusCode.Unauthorized
        )
        val clientApi: ClientApi = ClientApiImp(httpClient)
        val error = assertFailsWith<ClientRequestException> { clientApi.registerClient(Register_Client_REQUEST) }
        assertEquals(error.response.receive<ErrorResponse>(), ERROR_RESPONSE)
    }


    private companion object {

        const val PATH_CLIENTS = "clients"

        val TEST_PRES_KEY_1 = PreKey(1, "pre_key_1")
        val TEST_PRES_KEY_2 = PreKey(2, "pre_key_2")
        val TEST_PRES_KEY_3 = PreKey(3, "pre_key_3")
        val TEST_LAST_KEY = PreKey(101, "last_key")


        val Register_Client_REQUEST = RegisterClientRequest(
            password = "password",
            deviceType = DeviceType.Desktop,
            type = ClientType.Temporary,
            label = "label",
            preKeys = listOf(TEST_PRES_KEY_1, TEST_PRES_KEY_2, TEST_PRES_KEY_3),
            lastKey = TEST_LAST_KEY
        )

        val VALID_Register_Client_RESPONSE = RegisterClientResponse(
            clientId = "client_id",
            registrationTime = "12.34.56.78",
            location = LocationResponse(latitude = "1.234", longitude = "5.678"),
            type = ClientType.Temporary,
            deviceType = DeviceType.Desktop,
            label = "label",
            capabilities = Capabilities(capabilities = listOf())
        )

        val ERROR_RESPONSE = ErrorResponse(403, "unauthorized", "unauthorized")
    }
}
