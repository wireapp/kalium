package com.wire.kalium.logic.data.asset

import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.PlainData
import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.cryptography.utils.calcFileSHA256
import com.wire.kalium.cryptography.utils.encryptDataWithAES256
import com.wire.kalium.cryptography.utils.encryptFileWithAES256
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import com.wire.kalium.logic.EncryptionFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.user.AssetId
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.fileExtension
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.asset.AssetApi
import com.wire.kalium.network.api.base.authenticated.asset.AssetResponse
import com.wire.kalium.network.api.base.model.ErrorResponse
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
import okio.Buffer
import okio.Path
import okio.buffer
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
            .withRawStoredData(dummyData, fullDataPath)
            .withSuccessfulUpload(expectedAssetResponse)
            .arrange()

        // When
        val actual = assetRepository.uploadAndPersistPublicAsset(
            assetDataPath = fullDataPath,
            mimeType = "image/jpg",
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
            .withRawStoredData(dummyData, fullDataPath)
            .withSuccessfulUpload(expectedAssetResponse)
            .arrange()

        // When
        val actual = assetRepository.uploadAndPersistPrivateAsset(
            assetDataPath = fullDataPath,
            mimeType = "image/jpg",
            otrKey = randomAES256Key,
            extension = null
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
            .withRawStoredData(dummyData, fullDataPath)
            .withErrorUploadResponse()
            .arrange()

        // When
        val actual = assetRepository.uploadAndPersistPublicAsset(
            mimeType = "image/jpg",
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
            .withRawStoredData(dummyData, fullDataPath)
            .withErrorUploadResponse()
            .arrange()

        // When
        val actual = assetRepository.uploadAndPersistPrivateAsset(
            mimeType = "image/jpg",
            assetDataPath = fullDataPath,
            otrKey = randomAES256Key,
            extension = null
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
        val assetsToPersist = listOf(
            AssetId("value1", "domain1") to byteArrayOf(1, 1, 1),
            AssetId("value2", "domain2") to byteArrayOf(2, 2, 2)
        )
        val assetsIds = assetsToPersist.map { it.first }

        val (arrangement, assetRepository) = Arrangement()
            .withSuccessfulDownloadAndPersistedData(assetsToPersist)
            .arrange()

        // When
        val actual = assetRepository.downloadUsersPictureAssets(assetsIds)

        // Then
        actual.shouldSucceed {
            assertEquals(it, Unit)
        }

        verify(arrangement.assetDAO).suspendFunction(arrangement.assetDAO::insertAsset)
            .with(any())
            .wasInvoked(exactly = twice)
    }

    @Test
    fun givenAnAssetId_whenDownloadingNonLocalPublicAssets_thenShouldReturnItsDataPathFromRemoteAndPersistIt() = runTest {
        // Given
        val assetKey = AssetId("value1", "domain1")
        val assetData = listOf(assetKey to byteArrayOf(1, 10, 100))

        val (arrangement, assetRepository) = Arrangement()
            .withSuccessfulDownloadAndPersistedData(assetData)
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
                .with(matching { it.value == assetKey.value }, eq(null), any())
                .wasInvoked(exactly = once)
            verify(assetDAO)
                .suspendFunction(assetDAO::insertAsset)
                .with(any())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenAnAssetId_whenDownloadingPrivateAssetsAndNotPresentInDB_thenShouldReturnItsDataPathFromRemoteAndPersistIt() = runTest {
        // Given
        val assetKey = UserAssetId("value1", "domain1")
        val assetName = "Eiffel Tower.jpg"
        val assetToken = "some-token"
        val assetEncryptionKey = generateRandomAES256Key()
        val assetRawData = assetName.encodeToByteArray()
        val encryptedDataPath = encryptDataWithPath(assetRawData, assetEncryptionKey)
        val assetSha256 = calcFileSHA256(fakeKaliumFileSystem.source(encryptedDataPath))
        val assetEncryptedData = fakeKaliumFileSystem.source(encryptedDataPath).buffer().use {
            it.readByteArray()
        }

        val (arrangement, assetRepository) = Arrangement()
            .withSuccessfulDownloadAndPersistedData(listOf(assetKey to assetEncryptedData))
            .withMockedAssetDaoGetByKeyCall(assetKey, null)
            .arrange()

        // When
        val result = assetRepository.fetchPrivateDecodedAsset(assetKey, assetName, assetToken, assetEncryptionKey, SHA256Key(assetSha256!!))

        // Then
        with(arrangement) {
            assertTrue(result is Either.Right)
            val expectedPath = fakeKaliumFileSystem.providePersistentAssetPath("${assetKey.value}.${assetName.fileExtension()}")
            val realPath = result.value
            assertEquals(expectedPath, realPath)
            verify(assetDAO).suspendFunction(assetDAO::getAssetByKey)
                .with(eq(assetKey.value))
                .wasInvoked(exactly = once)
            verify(assetApi).suspendFunction(assetApi::downloadAsset)
                .with(matching { it.value == assetKey.value }, eq(assetToken), any())
                .wasInvoked(exactly = once)
            verify(assetDAO)
                .suspendFunction(assetDAO::insertAsset)
                .with(any())
                .wasInvoked(exactly = once)
        }
    }
    
    // @SF.Messages @TSFI.UserInterface @S0.1 @S1
    @Test
    fun givenAnAssetId_whenDownloadingPrivateAssetWithWrongAssetHash_thenShouldReturnAWrongAssetHashError() = runTest {
        // Given
        val assetKey = UserAssetId("value1", "domain1")
        val assetName = "Eiffel Tower.jpg"
        val assetToken = "some-token"
        val assetEncryptionKey = generateRandomAES256Key()
        val assetRawData = assetName.encodeToByteArray()
        val encryptedDataPath = encryptDataWithPath(assetRawData, assetEncryptionKey)
        val wrongAssetSha256 = calcFileSHA256(fakeKaliumFileSystem.source(encryptedDataPath))?.copyOf().apply {
            this?.set(0, 99)
        }
        val assetEncryptedData = fakeKaliumFileSystem.source(encryptedDataPath).buffer().use {
            it.readByteArray()
        }

        val (arrangement, assetRepository) = Arrangement()
            .withSuccessfulDownloadAndPersistedData(listOf(assetKey to assetEncryptedData))
            .withMockedAssetDaoGetByKeyCall(assetKey, null)
            .arrange()

        // When
        val result =
            assetRepository.fetchPrivateDecodedAsset(assetKey, assetName, assetToken, assetEncryptionKey, SHA256Key(wrongAssetSha256!!))

        // Then
        with(arrangement) {
            assertTrue(result is Either.Left)
            assertTrue(result.value is EncryptionFailure.WrongAssetHash)
        }
    }

    @Test
    fun givenAnError_whenDownloadingPublicAsset_thenShouldReturnThrowNetworkFailure() = runTest {
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

        assertTrue(actual is Either.Left)

        with(arrangement) {
            verify(assetDAO).suspendFunction(assetDAO::getAssetByKey)
                .with(matching { it == assetKey.value })
                .wasInvoked(exactly = once)
            verify(assetApi).suspendFunction(assetApi::downloadAsset)
                .with(matching { it.value == assetKey.value }, eq(null), any())
                .wasInvoked(exactly = once)
            verify(assetDAO).suspendFunction(assetDAO::insertAsset)
                .with(any())
                .wasNotInvoked()
        }
    }

    @Test
    fun givenAnError_whenDownloadingPrivateAsset_thenShouldReturnThrowNetworkFailure() = runTest {
        // Given
        val assetKey = UserAssetId("value1", "domain1")
        val assetName = "La Gioconda.jpg"
        val encryptionKey = AES256Key("some-encryption-key".toByteArray())
        val assetSha256 = SHA256Key(byteArrayOf(1, 2, 3))

        val (arrangement, assetRepository) = Arrangement()
            .withMockedAssetDaoGetByKeyCall(assetKey, null)
            .withErrorDownloadResponse()
            .arrange()

        // When
        val actual = assetRepository.fetchPrivateDecodedAsset(assetKey, assetName, null, encryptionKey, assetSha256)

        // Then
        actual.shouldFail {
            assertEquals(it::class, NetworkFailure.ServerMiscommunication::class)
        }

        assertTrue(actual is Either.Left)

        with(arrangement) {
            verify(assetDAO).suspendFunction(assetDAO::getAssetByKey)
                .with(matching { it == assetKey.value })
                .wasInvoked(exactly = once)
            verify(assetApi).suspendFunction(assetApi::downloadAsset)
                .with(matching { it.value == assetKey.value }, eq(null), any())
                .wasInvoked(exactly = once)
            verify(assetDAO).suspendFunction(assetDAO::insertAsset)
                .with(any())
                .wasNotInvoked()
        }
    }

    @Test
    fun givenAnAssetId_whenAssetIsAlreadyDownloaded_thenShouldReturnItsBinaryDataFromDB() = runTest {
        // Given
        val assetKey = UserAssetId("value1", "domain1")
        val expectedImage = "my_image_asset".toByteArray()
        val dummyPath = fakeKaliumFileSystem.providePersistentAssetPath("dummy_data_path")

        val (arrangement, assetRepository) = Arrangement()
            .withSuccessfulDownload(listOf(assetKey))
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
                .with(matching { it.value == assetKey.value }, eq(null), any())
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

    @Test
    fun givenSuccessfulResponse_whenDeletingRemotelyAsset_thenDeleteAssetLocallyShouldBeInvoked() = runTest {
        // Given
        val assetKey = UserAssetId("value1", "domain1")

        val (arrangement, assetRepository) = Arrangement()
            .withSuccessDeleteRemotelyResponse()
            .withSuccessDeleteLocallyResponse()
            .arrange()

        // When
        assetRepository.deleteAsset(assetKey, "asset-token")

        // Then
        verify(arrangement.assetApi).suspendFunction(arrangement.assetApi::deleteAsset)
            .with(any(), any())
            .wasInvoked(exactly = once)

        verify(arrangement.assetDAO).suspendFunction(arrangement.assetDAO::deleteAsset)
            .with(any())
            .wasInvoked(exactly = once)

    }

    class Arrangement {

        @Mock
        val assetApi = mock(classOf<AssetApi>())

        @Mock
        val assetDAO = mock(classOf<AssetDAO>())

        private val assetMapper by lazy { AssetMapperImpl() }

        private val assetRepository = AssetDataSource(assetApi, assetDAO, assetMapper, fakeKaliumFileSystem)

        fun withRawStoredData(data: ByteArray, dataPath: Path): Arrangement = apply {
            fakeKaliumFileSystem.sink(dataPath).buffer().use { it.write(data) }
        }

        fun withSuccessfulUpload(expectedAssetResponse: AssetResponse): Arrangement = apply {
            given(assetDAO)
                .suspendFunction(assetDAO::insertAsset)
                .whenInvokedWith(any())
                .thenDoNothing()

            given(assetApi)
                .suspendFunction(assetApi::uploadAsset)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(NetworkResponse.Success(expectedAssetResponse, mapOf(), 200))
        }

        fun withSuccessfulDownload(assetsIdToPersist: List<AssetId>): Arrangement = apply {
            assetsIdToPersist.forEach { assetKey ->
                withMockedAssetDaoGetByKeyCall(assetKey, null)
                given(assetApi)
                    .suspendFunction(assetApi::downloadAsset)
                    .whenInvokedWith(any(), any(), any())
                    .thenReturn(NetworkResponse.Success(Unit, mapOf(), 200))
                given(assetApi)
                    .suspendFunction(assetApi::downloadAsset)
                    .whenInvokedWith(any(), eq(null), any())
                    .thenReturn(NetworkResponse.Success(Unit, mapOf(), 200))

                given(assetDAO)
                    .suspendFunction(assetDAO::insertAsset)
                    .whenInvokedWith(any())
                    .thenDoNothing()
            }
        }

        fun withSuccessfulDownloadAndPersistedData(assetsIdToPersist: List<Pair<AssetId, ByteArray>>): Arrangement = apply {
            assetsIdToPersist.forEach { (assetKey, assetData) ->
                withMockedAssetDaoGetByKeyCall(assetKey, null)
                given(assetApi)
                    .suspendFunction(assetApi::downloadAsset)
                    .whenInvokedWith(any(), any(), matching {
                        val buffer = Buffer()
                        buffer.write(assetData)
                        it.write(buffer, assetData.size.toLong())
                        true
                    })
                    .thenReturn(NetworkResponse.Success(Unit, mapOf(), 200))
                given(assetApi)
                    .suspendFunction(assetApi::downloadAsset)
                    .whenInvokedWith(any(), eq(null), matching {
                        val buffer = Buffer()
                        buffer.write(assetData)
                        it.write(buffer, assetData.size.toLong())
                        true
                    })
                    .thenReturn(NetworkResponse.Success(Unit, mapOf(), 200))

                given(assetDAO)
                    .suspendFunction(assetDAO::insertAsset)
                    .whenInvokedWith(any())
                    .thenDoNothing()
            }
        }

        fun withErrorUploadResponse(): Arrangement = apply {
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
        }

        fun withErrorDownloadResponse(): Arrangement = apply {
            given(assetApi)
                .suspendFunction(assetApi::downloadAsset)
                .whenInvokedWith(anything(), anything(), anything())
                .thenReturn(
                    NetworkResponse.Error(
                        KaliumException.ServerError(
                            ErrorResponse(400, "error_message", "error_label")
                        )
                    )
                )
        }

        fun withMockedAssetDaoGetByKeyCall(assetKey: UserAssetId, expectedAssetEntity: AssetEntity?): Arrangement = apply {
            given(assetDAO)
                .suspendFunction(assetDAO::getAssetByKey)
                .whenInvokedWith(eq(assetKey.value))
                .thenReturn(flowOf(expectedAssetEntity))
        }

        fun withErrorDeleteResponse(): Arrangement = apply {
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
        }

        fun withSuccessDeleteRemotelyResponse(): Arrangement = apply {
            given(assetApi)
                .suspendFunction(assetApi::deleteAsset)
                .whenInvokedWith(anything(), anything())
                .thenReturn(NetworkResponse.Success(Unit, mapOf(), 200))
        }

        fun withSuccessDeleteLocallyResponse(): Arrangement = apply {
            given(assetDAO)
                .suspendFunction(assetDAO::deleteAsset)
                .whenInvokedWith(anything())
                .thenReturn(Unit)
        }

        fun arrange(): Pair<Arrangement, AssetRepository> = this to assetRepository
    }

    companion object {
        val fakeKaliumFileSystem: FakeKaliumFileSystem = FakeKaliumFileSystem()
    }

    private fun stubAssetEntity(assetKey: String, dataPath: Path, dataSize: Long) =
        AssetEntity(assetKey, "domain", dataPath.toString(), dataSize, null, 1)

    private fun encryptDataWithPath(data: ByteArray, assetEncryptionKey: AES256Key): Path = with(fakeKaliumFileSystem) {
        val rawDataPath = tempFilePath("input")
        val encryptedDataPath = tempFilePath("output")
        sink(rawDataPath).buffer().use { it.write(data) }
        encryptFileWithAES256(source(rawDataPath), assetEncryptionKey, sink(encryptedDataPath))

        encryptedDataPath
    }
}
