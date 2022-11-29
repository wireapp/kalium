package com.wire.kalium.api.v3

import com.wire.kalium.api.ApiTest
import com.wire.kalium.model.SendMLSMessageResponseJson
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.network.api.v3.authenticated.MLSMessageApiV3
import com.wire.kalium.network.serialization.XProtoBuf
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class MLSMessageApiV3Test : ApiTest {

    @Test
    fun givenMessage_whenSendingMessage_theRequestShouldBeConfiguredCorrectly() =
        runTest {
            val networkClient = mockAuthenticatedNetworkClient(
                SendMLSMessageResponseJson.validMessageSentJson.rawJson,
                statusCode = HttpStatusCode.Created,
                assertion =
                {
                    assertPost()
                    assertContentType(ContentType.Application.XProtoBuf)
                    assertPathEqual(PATH_COMMIT_BUNDLES)
                }
            )
            val mlsMessageApi: MLSMessageApi = MLSMessageApiV3(networkClient)
            val response = mlsMessageApi.sendCommitBundle(COMMIT_BUNDLE)
            assertTrue(response.isSuccessful())
        }

    private companion object {
        const val PATH_COMMIT_BUNDLES = "mls/commit-bundles"
        val COMMIT_BUNDLE = MLSMessageApi.CommitBundle("CommitBundle".encodeToByteArray())
    }

}
