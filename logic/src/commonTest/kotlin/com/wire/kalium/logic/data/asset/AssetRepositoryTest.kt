package com.wire.kalium.logic.data.asset

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.api.asset.AssetApi
import com.wire.kalium.network.api.asset.AssetResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.client.call.HttpClientCall
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Headers
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.util.InternalAPI
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AssetRepositoryTest {

    @Mock
    private val assetApi = mock(classOf<AssetApi>())

    private lateinit var assetRepository: AssetRepository

    @BeforeTest
    fun setUp() {
        assetRepository = AssetDataSource(assetApi, AssetMapperImpl())
    }

    @Test
    fun givenValidParams_whenUploadingPublicAssets_thenShouldSucceedWithAMappedResponse() = runTest {
        given(assetApi)
            .suspendFunction(assetApi::uploadAsset)
            .whenInvokedWith(any(), any())
            .thenReturn(NetworkResponse.Success(FakeHttpResponse(), AssetResponse("some_key", "some_expiration_val", "some_token")))

        val uploadAssetMetadata = UploadAssetData("the_image".encodeToByteArray(), ImageAsset.JPG, true, RetentionType.ETERNAL)

        val actual = assetRepository.uploadPublicAsset(uploadAssetMetadata)

        actual.shouldSucceed {
            assertEquals("some_key", it.key)
        }

        verify(assetApi).suspendFunction(assetApi::uploadAsset)
            .with(any(), any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAnError_whenUploadingPublicAssets_thenShouldFail() = runTest {
        given(assetApi)
            .suspendFunction(assetApi::uploadAsset)
            .whenInvokedWith(any(), any())
            .thenReturn(NetworkResponse.Error(KaliumException.ServerError(ErrorResponse(500, "error_message", "error_label"))))

        val uploadAssetMetadata = UploadAssetData("the_image".encodeToByteArray(), ImageAsset.JPG, true, RetentionType.ETERNAL)

        val actual = assetRepository.uploadPublicAsset(uploadAssetMetadata)

        actual.shouldFail {
            assertEquals(it::class, CoreFailure.ServerMiscommunication::class)
        }

        verify(assetApi).suspendFunction(assetApi::uploadAsset)
            .with(any(), any())
            .wasInvoked(exactly = once)
    }

    companion object {
        class FakeHttpResponse : HttpResponse() {
            override val call: HttpClientCall
                get() = TODO("Not yet implemented")

            @InternalAPI
            override val content: ByteReadChannel
                get() = TODO("Not yet implemented")
            override val coroutineContext: CoroutineContext
                get() = TODO("Not yet implemented")
            override val headers: Headers
                get() = TODO("Not yet implemented")
            override val requestTime: GMTDate
                get() = TODO("Not yet implemented")
            override val responseTime: GMTDate
                get() = TODO("Not yet implemented")
            override val status: HttpStatusCode
                get() = TODO("Not yet implemented")
            override val version: HttpProtocolVersion
                get() = TODO("Not yet implemented")
        }
    }
}
