package com.wire.kalium.logic.data.asset

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.api.asset.AssetApi
import com.wire.kalium.network.api.asset.AssetResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.asset.AssetDAO
import com.wire.kalium.persistence.dao.asset.AssetEntity
import io.ktor.utils.io.core.toByteArray
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.thenDoNothing
import io.mockative.twice
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AssetRepositoryTest {

    @Mock
    private val assetApi = mock(classOf<AssetApi>())

    @Mock
    private val assetDAO = mock(classOf<AssetDAO>())

    private lateinit var assetRepository: AssetRepository

    private val assetMapper by lazy { AssetMapperImpl() }

    @BeforeTest
    fun setUp() {
        assetRepository = AssetDataSource(assetApi, assetMapper, assetDAO)
    }

    @Test
    fun givenValidParams_whenUploadingPublicAssets_thenShouldSucceedWithAMappedResponse() = runTest {
        given(assetDAO)
            .suspendFunction(assetDAO::insertAsset)
            .whenInvokedWith(any())
            .thenDoNothing()

        given(assetApi)
            .suspendFunction(assetApi::uploadAsset)
            .whenInvokedWith(any(), any())
            .thenReturn(
                NetworkResponse.Success(
                    AssetResponse("some_key", "some_domain", "some_expiration_val", "some_token"),
                    mapOf(),
                    200
                )
            )

        val uploadAssetMetadata = UploadAssetData("the_image".encodeToByteArray(), ImageAsset.JPG, true, RetentionType.ETERNAL)

        val actual = assetRepository.uploadAndPersistPublicAsset(uploadAssetMetadata)

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

        val actual = assetRepository.uploadAndPersistPublicAsset(uploadAssetMetadata)

        actual.shouldFail {
            assertEquals(it::class, NetworkFailure.ServerMiscommunication::class)
        }

        verify(assetApi).suspendFunction(assetApi::uploadAsset)
            .with(any(), any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAListOfAssets_whenSavingAssets_thenShouldSucceed() = runTest {
        val assetsIdToPersist = listOf<UserAssetId>("assetId1", "assetId2")
        val expectedImage = "my_image_asset".toByteArray()

        assetsIdToPersist.forEach { assetKey ->
            mockAssetDaoGetByKeyCall(assetKey, null)
            given(assetApi)
                .suspendFunction(assetApi::downloadAsset)
                .whenInvokedWith(eq(assetKey), eq(null))
                .thenReturn(NetworkResponse.Success(expectedImage, mapOf(), 200))

            given(assetDAO)
                .suspendFunction(assetDAO::insertAsset)
                .whenInvokedWith(any())
                .thenDoNothing()
        }

        val actual = assetRepository.downloadUsersPictureAssets(assetsIdToPersist)

        actual.shouldSucceed {
            assertEquals(it, Unit)
        }

        verify(assetDAO).suspendFunction(assetDAO::insertAsset)
            .with(any())
            .wasInvoked(exactly = twice)
    }

    @Test
    fun givenAnAssetId_whenDownloadingAssetsAndNotPresentInDB_thenShouldReturnItsBinaryDataFromRemoteAndPersistIt() = runTest {
        val assetKey = "1-3-an-asset-key"
        val expectedImage = "my_image_asset".toByteArray()

        mockAssetDaoGetByKeyCall(assetKey, null)
        given(assetApi)
            .suspendFunction(assetApi::downloadAsset)
            .whenInvokedWith(eq(assetKey), eq(null))
            .thenReturn(NetworkResponse.Success(expectedImage, mapOf(), 200))
        given(assetDAO)
            .suspendFunction(assetDAO::insertAsset)
            .whenInvokedWith(any())
            .thenReturn(Unit)

        val actual = assetRepository.downloadPublicAsset(assetKey)

        actual.shouldSucceed {
            assertEquals(it, expectedImage)
        }

        verify(assetDAO).suspendFunction(assetDAO::getAssetByKey)
            .with(eq(assetKey))
            .wasInvoked(exactly = once)
        verify(assetApi).suspendFunction(assetApi::downloadAsset)
            .with(eq(assetKey), eq(null))
            .wasInvoked(exactly = once)
        verify(assetDAO)
            .suspendFunction(assetDAO::insertAsset)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAnError_whenDownloadingAssets_thenShouldReturnThrowNetworkFailure() = runTest {
        val assetKey = "1-3-an-asset-key"
        val expectedImage = "my_image_asset".toByteArray()
        mockAssetDaoGetByKeyCall(assetKey, null)
        given(assetApi)
            .suspendFunction(assetApi::downloadAsset)
            .whenInvokedWith(eq(assetKey), eq(null))
            .thenReturn(NetworkResponse.Error(KaliumException.ServerError(ErrorResponse(500, "error_message", "error_label"))))

        val actual = assetRepository.downloadPublicAsset(assetKey)

        actual.shouldFail {
            assertEquals(it::class, NetworkFailure.ServerMiscommunication::class)
        }

        verify(assetDAO).suspendFunction(assetDAO::getAssetByKey)
            .with(eq(assetKey))
            .wasInvoked(exactly = once)

        verify(assetApi).suspendFunction(assetApi::downloadAsset)
            .with(eq(assetKey), eq(null))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAnAssetId_whenAssetIsAlreadyDownloaded_thenShouldReturnItsBinaryDataFromDB() = runTest {
        val assetKey = "1-3-an-asset-key"
        val expectedImage = "my_image_asset".toByteArray()
        mockAssetDaoGetByKeyCall(assetKey, stubAssetEntity(assetKey, expectedImage))

        val actual = assetRepository.downloadPublicAsset(assetKey)

        actual.shouldSucceed {
            assertEquals(expectedImage, it)
        }

        verify(assetDAO).suspendFunction(assetDAO::getAssetByKey)
            .with(eq(assetKey))
            .wasInvoked(exactly = once)

        verify(assetApi).suspendFunction(assetApi::downloadAsset)
            .with(eq(assetKey), eq(null))
            .wasNotInvoked()
    }

    private fun mockAssetDaoGetByKeyCall(assetKey: String, expectedAssetEntity: AssetEntity?) {
        given(assetDAO)
            .suspendFunction(assetDAO::getAssetByKey)
            .whenInvokedWith(eq(assetKey))
            .thenReturn(flowOf(expectedAssetEntity))
    }

    private fun stubAssetEntity(assetKey: String, rawData: ByteArray) =
        AssetEntity(assetKey, "some_domain", null, rawData, 1)
}
