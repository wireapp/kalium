package com.wire.kalium.api.tools.json.api.connection

import com.wire.kalium.api.ApiTest
import com.wire.kalium.network.api.user.connection.ConnectionApi
import com.wire.kalium.network.api.user.connection.ConnectionApiImpl
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ConnectionApiTest : ApiTest {

    @Test
    fun givenAGetConnectionsRequest_whenRequestingAllConnectionsWithSuccess_thenRequestShouldBeConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            GET_CONNECTIONS_RESPONSE.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertJson()
                assertPost()
                assertBodyContent(GET_CONNECTIONS_NO_PAGING_REQUEST.rawJson)
                assertPathEqual(PATH_CONNECTIONS)
            }
        )

        val connectionApi: ConnectionApi = ConnectionApiImpl(networkClient)
        connectionApi.fetchSelfUserConnections(pagingState = null)
    }

    @Test
    fun givenAGetConnectionsRequestWithPaging_whenRequestingAllConnectionsWithSuccess_thenRequestShouldBeConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            GET_CONNECTIONS_RESPONSE.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertJson()
                assertPost()
                assertBodyContent(GET_CONNECTIONS_WITH_PAGING_REQUEST.rawJson)
                assertPathEqual(PATH_CONNECTIONS)
            }
        )

        val connectionApi: ConnectionApi = ConnectionApiImpl(networkClient)
        connectionApi.fetchSelfUserConnections(pagingState = null)
    }

    private companion object {
        const val PATH_CONNECTIONS = "/list-connections"

        val GET_CONNECTIONS_RESPONSE = ConnectionResponsesJson.GetConnections.validGetConnections
        val GET_CONNECTIONS_NO_PAGING_REQUEST = ConnectionRequestsJson.validEmptyBody
        val GET_CONNECTIONS_WITH_PAGING_REQUEST = ConnectionRequestsJson.validPagingState
    }
}
