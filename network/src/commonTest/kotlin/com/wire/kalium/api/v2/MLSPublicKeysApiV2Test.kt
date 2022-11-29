package com.wire.kalium.api.v2

import com.wire.kalium.api.ApiTest
import com.wire.kalium.model.MLSPublicKeysResponseJson
import com.wire.kalium.network.api.base.authenticated.serverpublickey.MLSPublicKeyApi
import com.wire.kalium.network.api.v2.authenticated.MLSPublicKeyApiV2
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MLSPublicKeysApiV2Test : ApiTest {

    @Test
    fun givenWhenGetMLSPublicKeys_theRequestShouldBeConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            MLSPublicKeysResponseJson.valid.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion =
            {
                assertGet()
                assertContentType(ContentType.Application.Json)
                assertPathEqual("$PATH_MLS/$PATH_PUBLIC_KEYS")
            }
        )
        val mlsPublicKeyApi: MLSPublicKeyApi = MLSPublicKeyApiV2(networkClient)
        val response = mlsPublicKeyApi.getMLSPublicKeys()
        assertTrue(response.isSuccessful())
    }

    private companion object {
        const val PATH_PUBLIC_KEYS = "public-keys"
        const val PATH_MLS = "mls"
    }
}
