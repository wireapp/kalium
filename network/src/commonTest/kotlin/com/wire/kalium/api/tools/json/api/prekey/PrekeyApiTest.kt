package com.wire.kalium.api.tools.json.api.prekey

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.tools.json.model.DomainToUserIdToClientToPreKeyMapJson
import com.wire.kalium.api.tools.json.model.DomainToUserIdToClientsMapJson
import com.wire.kalium.api.tools.json.model.ErrorResponseJson
import com.wire.kalium.network.api.prekey.PreKeyApi
import com.wire.kalium.network.api.prekey.PreKeyApiImpl
import io.ktor.client.features.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@ExperimentalCoroutinesApi
class PrekeyApiTest : ApiTest {

    @Test
    fun givenAValidDomainToUserIdToClientsMap_whenCallingGetUsersPrekeyEndpoint_theRequestShouldBeConfiguredCorrectly() = runTest {
        val httpClient = mockHttpClient(
            VALID_GET_USERS_PREKEY_RESPONSE.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertJson()
                assertPathEqual(PATH_PREYKEY)
            }
        )
        val preKeyApi: PreKeyApi = PreKeyApiImpl(httpClient)

        val response = preKeyApi.getUsersPreKey(VALID_GET_USERS_PREKEY_REQUEST.serializableData)
        //
        assertEquals(response.resultBody, VALID_GET_USERS_PREKEY_RESPONSE.serializableData)
    }

    @Test
    fun givenAnInvalidDomainToUserIdToClientsMap_whenCallingGetUsersPrekeyEndpoint_theCorrectExceptionIsThrown() = runTest {
        val httpClient = mockHttpClient(
            ErrorResponseJson.valid.rawJson,
            statusCode = HttpStatusCode.Forbidden,
        )
        val preKeyApi: PreKeyApi = PreKeyApiImpl(httpClient)

        assertFailsWith<ClientRequestException> { preKeyApi.getUsersPreKey(VALID_GET_USERS_PREKEY_REQUEST.serializableData) }

    }


    private companion object {
        val VALID_GET_USERS_PREKEY_REQUEST = DomainToUserIdToClientsMapJson.valid
        val VALID_GET_USERS_PREKEY_RESPONSE = DomainToUserIdToClientToPreKeyMapJson.valid
        const val PATH_PREYKEY = "/users/list-prekeys"
    }
}
