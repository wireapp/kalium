package com.wire.kalium.api.tools.json.api.call

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.tools.json.AnyResponseProvider
import com.wire.kalium.network.api.call.CallApi
import com.wire.kalium.network.api.call.CallApiImpl
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@ExperimentalCoroutinesApi
class CallApiTest : ApiTest {

    @Test
    fun givenCallApi_whenGettingCallConfigWithNoLimit_theRequestShouldBeConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = GET_CALL_CONFIG.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertPathEqual("/$PATH_CALLS/$PATH_CONFIG")
            }
        )

        val callApi: CallApi = CallApiImpl(networkClient)
        callApi.getCallConfig(limit = null)
    }

    @Test
    fun givenCallApi_whenGettingCallConfigWithLimit_theRequestShouldBeConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = GET_CALL_CONFIG.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertQueryExist(QUERY_KEY_LIMIT)
                assertQueryParameter(QUERY_KEY_LIMIT, hasValue = "7")
                assertPathEqual("/$PATH_CALLS/$PATH_CONFIG")
            }
        )

        val callApi: CallApi = CallApiImpl(networkClient)
        callApi.getCallConfig(limit = 7)
    }

    @Test
    fun givenCallApi_whenConnectingToSFT_theREquestShouldBeConfiguredCorrectly() = runTest {
        val sftConnectionURL = "sft.connection.url1"
        val sftConnectionData = """
            |{
            |   "sft":"connection"
            |}
        """.trimIndent()

        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = GET_CALL_SFT.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertJson()
                assertPost()
                assertPathEqual(sftConnectionURL)
                assertBodyContent(sftConnectionData)
            }
        )

        val callApi: CallApi = CallApiImpl(networkClient)
        callApi.connectToSFT(
            url = sftConnectionURL,
            data = sftConnectionData
        )
    }

    private companion object {

        const val PATH_CALLS = "calls"
        const val PATH_CONFIG = "config/v2"

        const val QUERY_KEY_LIMIT = "limit"

        val jsonProvider = { _: String ->
            """
            |{
            |   "call": "dummy_config"
            |}
            """.trimIndent()
        }
        val GET_CALL_CONFIG = AnyResponseProvider(data = "", jsonProvider)
        val GET_CALL_SFT = AnyResponseProvider(data = "", jsonProvider)
    }
}
