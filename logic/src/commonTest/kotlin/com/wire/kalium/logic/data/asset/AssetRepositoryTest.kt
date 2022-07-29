package com.wire.kalium.logic.data.asset

import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.user.AssetId
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
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.thenDoNothing
import io.mockative.twice
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.buffer
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AssetRepositoryTest {

    @Test
    fun givenValidParams_whenUploadingPublicAssets_thenShouldSucceedWithAMappedResponse() = runTest {
        // Given
        val dataNamePath = "temp-data-path"
        val fullDataPath = fakeKaliumFileSystem.tempFilePath(dataNamePath)
        val dummyData = "some-dummy-data".toByteArray()
        val expectedAssetResponse = AssetResponse("some_key", "some_domain", "some_expiration_val", "some_token")

        val (arrangement, assetRepository) = Arrangement()
            .withStoredData(dummyData, fullDataPath)
            .withSuccessfulUpload(expectedAssetResponse)
            .arrange()

        // When
        val actual = assetRepository.uploadAndPersistPublicAsset(
            assetDataPath = fullDataPath,
            mimeType = ImageAsset.JPEG,
            assetDataSize = dummyData.size.toLong()
        )

        // Then
        actual.shouldSucceed {
            assertEquals("some_key", it.key)
        }

        verify(arrangement.assetApi).suspendFunction(arrangement.assetApi::uploadAsset)
            .with(any(), any(), any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenValidParams_whenUploadingPrivateAssets_thenShouldSucceedWithAMappedResponse() = runTest {
        // Given
        val dataNamePath = "dummy-data-path"
        val fullDataPath = fakeKaliumFileSystem.tempFilePath(dataNamePath)
        val dummyData = "some-dummy-data".toByteArray()
        val randomAES256Key = generateRandomAES256Key()
        val expectedAssetResponse = AssetResponse("some_key", "some_domain", "some_expiration_val", "some_token")

        val (arrangement, assetRepository) = Arrangement()
            .withStoredData(dummyData, fullDataPath)
            .withSuccessfulUpload(expectedAssetResponse)
            .arrange()

        // When
        val actual = assetRepository.uploadAndPersistPrivateAsset(
            assetDataPath = fullDataPath,
            mimeType = ImageAsset.JPEG,
            otrKey = randomAES256Key
        )

        // Then
        actual.shouldSucceed {
            assertEquals(expectedAssetResponse.key, it.first.key)
            assertEquals(expectedAssetResponse.domain, it.first.domain)
            assertEquals(expectedAssetResponse.token, it.first.assetToken)
        }

        verify(arrangement.assetApi).suspendFunction(arrangement.assetApi::uploadAsset)
            .with(any(), any(), any())
            .wasInvoked(exactly = once)
        verify(arrangement.assetDAO).suspendFunction(arrangement.assetDAO::insertAsset)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAnError_whenUploadingPublicAssets_thenShouldFail() = runTest {
        // Given
        val dataNamePath = "dummy-data-path"
        val fullDataPath = fakeKaliumFileSystem.tempFilePath(dataNamePath)
        val dummyData = "some-dummy-data".toByteArray()
        val (arrangement, assetRepository) = Arrangement()
            .withStoredData(dummyData, fullDataPath)
            .withErrorUploadResponse()
            .arrange()

        // When
        val actual = assetRepository.uploadAndPersistPublicAsset(
            mimeType = ImageAsset.JPEG,
            assetDataPath = fullDataPath,
            assetDataSize = dummyData.size.toLong()
        )

        // Then
        actual.shouldFail {
            assertEquals(it::class, NetworkFailure.ServerMiscommunication::class)
        }

        verify(arrangement.assetApi).suspendFunction(arrangement.assetApi::uploadAsset)
            .with(any(), any(), any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAnError_whenUploadingPrivateAssets_thenShouldFail() = runTest {
        // Given
        val dummyPath = "dummy-data-path"
        val fullDataPath = fakeKaliumFileSystem.tempFilePath(dummyPath)
        val dummyData = "some-dummy-data".toByteArray()
        val randomAES256Key = generateRandomAES256Key()
        val (arrangement, assetRepository) = Arrangement()
            .withStoredData(dummyData, fullDataPath)
            .withErrorUploadResponse()
            .arrange()

        // When
        val actual = assetRepository.uploadAndPersistPrivateAsset(
            mimeType = ImageAsset.JPEG,
            assetDataPath = fullDataPath,
            otrKey = randomAES256Key
        )

        // Then
        actual.shouldFail {
            assertEquals(it::class, NetworkFailure.ServerMiscommunication::class)
        }

        verify(arrangement.assetApi).suspendFunction(arrangement.assetApi::uploadAsset)
            .with(any(), any(), any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAListOfAssets_whenSavingAssets_thenShouldSucceed() = runTest {
        // Given
        val assetsIdToPersist = listOf(AssetId("value1", "domain1"), AssetId("value2", "domain2"))
        val dummyData = "some-dummy-data".toByteArray()

        val (arrangement, assetRepository) = Arrangement()
            .withSuccessfulDownload(assetsIdToPersist, dummyData)
            .arrange()

        // When
        val actual = assetRepository.downloadUsersPictureAssets(assetsIdToPersist)

        // Then
        actual.shouldSucceed {
            assertEquals(it, Unit)
        }

        verify(arrangement.assetDAO).suspendFunction(arrangement.assetDAO::insertAsset)
            .with(any())
            .wasInvoked(exactly = twice)
    }

    @Test
    fun givenAnAssetId_whenDownloadingAssetsAndNotPresentInDB_thenShouldReturnItsBinaryDataFromRemoteAndPersistIt() = runTest {
        // Given
        val assetKey = UserAssetId("value1", "domain1")
        val dummyData = "some-dummy-data".toByteArray()

        val (arrangement, assetRepository) = Arrangement()
            .withSuccessfulDownload(listOf(assetKey), dummyData)
            .withMockedAssetDaoGetByKeyCall(assetKey, null)
            .arrange()

        // When
        assetRepository.downloadPublicAsset(assetKey)

        // Then
        with(arrangement) {
            verify(assetDAO).suspendFunction(assetDAO::getAssetByKey)
                .with(eq(assetKey.value))
                .wasInvoked(exactly = once)
            verify(assetApi).suspendFunction(assetApi::downloadAsset)
                .with(matching { it.value == assetKey.value }, eq(null))
                .wasInvoked(exactly = once)
            verify(assetDAO)
                .suspendFunction(assetDAO::insertAsset)
                .with(any())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenAnError_whenDownloadingAssets_thenShouldReturnThrowNetworkFailure() = runTest {
        // Given
        val assetKey = UserAssetId("value1", "domain1")

        val (arrangement, assetRepository) = Arrangement()
            .withMockedAssetDaoGetByKeyCall(assetKey, null)
            .withErrorDownloadResponse()
            .arrange()

        // When
        val actual = assetRepository.downloadPublicAsset(assetKey)

        // Then
        actual.shouldFail {
            assertEquals(it::class, NetworkFailure.ServerMiscommunication::class)
        }

        with(arrangement) {
            verify(assetDAO).suspendFunction(assetDAO::getAssetByKey)
                .with(matching { it == assetKey.value })
                .wasInvoked(exactly = once)
            verify(assetApi).suspendFunction(assetApi::downloadAsset)
                .with(matching { it.value == assetKey.value }, eq(null))
                .wasInvoked(exactly = once)

        }
    }

    @Test
    fun givenAnAssetId_whenAssetIsAlreadyDownloaded_thenShouldReturnItsBinaryDataFromDB() = runTest {
        // Given
        val assetKey = UserAssetId("value1", "domain1")
        val expectedImage = "my_image_asset".toByteArray()
        val dummyPath = fakeKaliumFileSystem.providePersistentAssetPath("dummy_data_path")

        val (arrangement, assetRepository) = Arrangement()
            .withSuccessfulDownload(listOf(assetKey), expectedImage)
            .withMockedAssetDaoGetByKeyCall(assetKey, stubAssetEntity(assetKey.value, dummyPath, expectedImage.size.toLong()))
            .arrange()
        // When
        assetRepository.downloadPublicAsset(assetKey)

        // Then
        with(arrangement) {
            verify(assetDAO).suspendFunction(assetDAO::getAssetByKey)
                .with(eq(assetKey.value))
                .wasInvoked(exactly = once)

            verify(assetApi).suspendFunction(assetApi::downloadAsset)
                .with(matching { it.value == assetKey.value }, eq(null))
                .wasNotInvoked()
        }
    }

    @Test
    fun givenAnApiError_whenDeletingRemotelyAsset_thenDeleteAssetLocallyShouldNotBeInvoked() = runTest {
        // Given
        val assetKey = UserAssetId("value1", "domain1")

        val (arrangement, assetRepository) = Arrangement()
            .withErrorDeleteResponse()
            .arrange()

        // When
        assetRepository.deleteAsset(assetKey, "asset-token")

        // Then
        verify(arrangement.assetApi).suspendFunction(arrangement.assetApi::deleteAsset)
            .with(any(), any())
            .wasInvoked(exactly = once)

        verify(arrangement.assetDAO).suspendFunction(arrangement.assetDAO::deleteAsset)
            .with(any())
            .wasNotInvoked()

    }

    class Arrangement {

        @Mock
        val assetApi = mock(classOf<AssetApi>())

        @Mock
        val assetDAO = mock(classOf<AssetDAO>())

        private val assetMapper by lazy { AssetMapperImpl() }

        private val assetRepository = AssetDataSource(assetApi, assetDAO, assetMapper, fakeKaliumFileSystem)

        fun withStoredData(data: ByteArray, dataPath: Path): Arrangement {
            fakeKaliumFileSystem.sink(dataPath).buffer().use {
                it.write(data)
                it.flush()
                it.close()
            }
            return this
        }

        fun withSuccessfulUpload(expectedAssetResponse: AssetResponse): Arrangement {
            given(assetDAO)
                .suspendFunction(assetDAO::insertAsset)
                .whenInvokedWith(any())
                .thenDoNothing()

            given(assetApi)
                .suspendFunction(assetApi::uploadAsset)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(NetworkResponse.Success(expectedAssetResponse, mapOf(), 200))
            return this
        }

        fun withSuccessfulDownload(assetsIdToPersist: List<AssetId>, expectedData: ByteArray): Arrangement {
            assetsIdToPersist.forEach { assetKey ->
                withMockedAssetDaoGetByKeyCall(assetKey, null)
                given(assetApi)
                    .suspendFunction(assetApi::downloadAsset)
                    .whenInvokedWith(matching { assetId -> assetId.value == assetKey.value }, eq(null))
                    .thenReturn(NetworkResponse.Success(expectedData, mapOf(), 200))

                given(assetDAO)
                    .suspendFunction(assetDAO::insertAsset)
                    .whenInvokedWith(any())
                    .thenDoNothing()
            }
            return this
        }

        fun withErrorUploadResponse(): Arrangement {
            given(assetApi)
                .suspendFunction(assetApi::uploadAsset)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(
                    NetworkResponse.Error(
                        KaliumException.ServerError(
                            ErrorResponse(500, "error_message", "error_label")
                        )
                    )
                )
            return this
        }

        fun withErrorDownloadResponse(): Arrangement {
            given(assetApi)
                .suspendFunction(assetApi::downloadAsset)
                .whenInvokedWith(anything(), anything())
                .thenReturn(
                    NetworkResponse.Error(
                        KaliumException.ServerError(
                            ErrorResponse(500, "error_message", "error_label")
                        )
                    )
                )
            return this
        }

        fun withMockedAssetDaoGetByKeyCall(assetKey: UserAssetId, expectedAssetEntity: AssetEntity?): Arrangement {
            given(assetDAO)
                .suspendFunction(assetDAO::getAssetByKey)
                .whenInvokedWith(eq(assetKey.value))
                .thenReturn(flowOf(expectedAssetEntity))
            return this
        }

        fun withErrorDeleteResponse(): Arrangement {
            given(assetApi)
                .suspendFunction(assetApi::deleteAsset)
                .whenInvokedWith(anything(), anything())
                .thenReturn(
                    NetworkResponse.Error(
                        KaliumException.ServerError(
                            ErrorResponse(500, "error_message", "error_label")
                        )
                    )
                )
            return this
        }

        fun arrange(): Pair<Arrangement, AssetRepository> = this to assetRepository
    }

    companion object {
        val fakeKaliumFileSystem: FakeKaliumFileSystem = FakeKaliumFileSystem()
    }

    private fun stubAssetEntity(assetKey: String, dataPath: Path, dataSize: Long) =
        AssetEntity(assetKey, "domain", null, dataPath.toString(), dataSize, null, 1)

}
