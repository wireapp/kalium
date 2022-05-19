package com.wire.kalium.api.tools.json.api.connection

import com.wire.kalium.api.ApiTest
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.user.connection.ConnectionApi
import com.wire.kalium.network.api.user.connection.ConnectionApiImpl
import com.wire.kalium.network.api.user.connection.ConnectionState
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

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

    @Test
    fun givenACreationRequest_whenRequestingAConnectionWithAnUser_thenShouldReturnsACorrectConnectionResponse() = runTest {
        // given
        val userId = UserId("user_id", "domain_id")
        val httpClient = mockAuthenticatedNetworkClient(
            CREATE_CONNECTION_RESPONSE.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertJson()
                assertPost()
                assertPathEqual("$PATH_CONNECTIONS_ENDPOINT/${userId.domain}/${userId.value}")
            }
        )
        val connectionApi = ConnectionApiImpl(httpClient)

        // when
        val response = connectionApi.createConnection(userId)

        // then
        assertTrue(response.isSuccessful())

    }

    @Test
    fun givenAConnectionRequestUpdate_whenInvokingWithAnUserAndAConnectionStatus_thenShouldReturnsACorrectConnectionResponse() =
        runTest {
            // given
            val userId = UserId("user_id", "domain_id")
            val httpClient = mockAuthenticatedNetworkClient(
                CREATE_CONNECTION_RESPONSE.rawJson,
                statusCode = HttpStatusCode.OK,
                assertion = {
                    assertJson()
                    assertPut()
                    assertPathEqual("$PATH_CONNECTIONS_ENDPOINT/${userId.domain}/${userId.value}")
                    assertBodyContent(GET_CONNECTION_STATUS_REQUEST.rawJson)
                }
            )
            val connectionApi = ConnectionApiImpl(httpClient)

            // when
            val response = connectionApi.updateConnection(userId, ConnectionState.ACCEPTED)

            // then
            assertTrue(response.isSuccessful())
        }


    private companion object {
        const val PATH_CONNECTIONS = "/list-connections"
        const val PATH_CONNECTIONS_ENDPOINT = "/connections"

        val GET_CONNECTIONS_RESPONSE = ConnectionResponsesJson.GetConnections.validGetConnections
        val CREATE_CONNECTION_RESPONSE = ConnectionResponsesJson.CreateConnectionResponse.jsonProvider
        val GET_CONNECTIONS_NO_PAGING_REQUEST = ConnectionRequestsJson.validEmptyBody
        val GET_CONNECTIONS_WITH_PAGING_REQUEST = ConnectionRequestsJson.validPagingState
        val GET_CONNECTION_STATUS_REQUEST = ConnectionRequestsJson.validConnectionStatusUpdate
    }
}
