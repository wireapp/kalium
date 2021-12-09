package com.wire.kalium.api.tools.json.api.user.self

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.tools.json.model.ErrorResponseJson
import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.api.user.self.SelfApi
import io.ktor.client.call.receive
import io.ktor.client.features.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@ExperimentalCoroutinesApi
class SelfApiTest : ApiTest {
    @Test
    fun givenAValidRegisterLogoutRequest_whenCallingTheRegisterLogoutEndpoint_theRequestShouldBeConfiguredCorrectly() =
        runTest {
            val httpClient = mockHttpClient(
                VALID_SELF_RESPONSE.rawJson,
                statusCode = HttpStatusCode.Created,
                assertion = {
                    assertGet()
                    assertNoQueryParams()
                    assertPathEqual(PATH_SELF)
                }
            )
            val selfApi: SelfApi = SelfApi(httpClient)
            val response = selfApi.getSelfInfo()
            assertEquals(response.resultBody, VALID_SELF_RESPONSE.serializableData)
        }

    @Test
    fun givenTheServerReturnsAnError_whenCallingTheGetSelfEndpoint_theCorrectExceptionIsThrown() = runTest {
        val httpClient = mockHttpClient(
            ErrorResponseJson.valid.rawJson,
            statusCode = HttpStatusCode.Unauthorized
        )
        val selfApi: SelfApi = SelfApi(httpClient)
        val error = assertFailsWith<ClientRequestException> { selfApi.getSelfInfo() }
        assertEquals(error.response.receive<ErrorResponse>(), ERROR_RESPONSE)
    }


    private companion object {
        const val PATH_SELF = "self"
        val VALID_SELF_RESPONSE = SelfUserInfoResponseJson.valid
        val ERROR_RESPONSE = ErrorResponseJson.valid.serializableData
    }
}
