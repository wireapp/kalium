package com.wire.kalium.api.tools.json.api.connection

import com.wire.kalium.api.ApiTest
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.user.connection.ConnectionApi
import com.wire.kalium.network.api.user.connection.ConnectionApiImpl
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ConnectionApiTest : ApiTest {

    @Test
    fun givenAGetConnectionsRequest_whenRequestingAllConnectionsWithSuccess_thenRequestShouldBeConfiguredCorrectly() = runTest {
        val httpClient = mockAuthenticatedHttpClient(
            GET_CONNECTIONS_RESPONSE.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertJson()
                assertPost()
                assertBodyContent(GET_CONNECTIONS_NO_PAGING_REQUEST.rawJson)
                assertPathEqual(PATH_CONNECTIONS)
            }
        )

        val connectionApi: ConnectionApi = ConnectionApiImpl(httpClient)
        connectionApi.fetchSelfUserConnections(pagingState = null)
    }

    @Test
    fun givenAGetConnectionsRequestWithPaging_whenRequestingAllConnectionsWithSuccess_thenRequestShouldBeConfiguredCorrectly() = runTest {
        val httpClient = mockAuthenticatedHttpClient(
            GET_CONNECTIONS_RESPONSE.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertJson()
                assertPost()
                assertBodyContent(GET_CONNECTIONS_WITH_PAGING_REQUEST.rawJson)
                assertPathEqual(PATH_CONNECTIONS)
            }
        )

        val connectionApi: ConnectionApi = ConnectionApiImpl(httpClient)
        connectionApi.fetchSelfUserConnections(pagingState = null)
    }

    @Test
    fun givenACreationRequest_whenRequestingAConnectionWithAnUser_thenShouldReturnsACorrectConnectionResponse() = runTest {
        val httpClient = mockAuthenticatedHttpClient(
            CREATE_CONNECTION_RESPONSE.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertJson()
                assertPost()
                assertPathEqual("$PATH_CONNECTIONS_ENDPOINT/domain_id/user_id")
            }
        )

        val connectionApi = ConnectionApiImpl(httpClient)
        val response = connectionApi.createConnection(UserId("user_id", "domain_id"))

        assertTrue(response.isSuccessful())
    }

    private companion object {
        const val PATH_CONNECTIONS = "/list-connections"
        const val PATH_CONNECTIONS_ENDPOINT = "/connections"

        val GET_CONNECTIONS_RESPONSE = ConnectionResponsesJson.GetConnections.validGetConnections
        val CREATE_CONNECTION_RESPONSE = ConnectionResponsesJson.CreateConnectionResponse.jsonProvider
        val GET_CONNECTIONS_NO_PAGING_REQUEST = ConnectionRequestsJson.validEmptyBody
        val GET_CONNECTIONS_WITH_PAGING_REQUEST = ConnectionRequestsJson.validPagingState
    }
}
