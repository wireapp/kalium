/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.data.asset

import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.cryptography.utils.calcFileSHA256
import com.wire.kalium.cryptography.utils.encryptFileWithAES256
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import com.wire.kalium.logic.EncryptionFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
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
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.Path
import okio.buffer
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

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
    fun givenAnAssetId_whenDownloadingNonLocalPublicAssets_thenShouldReturnItsDataPathFromRemoteAndPersistIt() = runTest {
        // Given
        val assetKey = AssetId("value1", "domain1")
        val assetData = listOf(assetKey to byteArrayOf(1, 10, 100))

        val (arrangement, assetRepository) = Arrangement()
            .withSuccessfulDownloadAndPersistedData(assetData)
            .withMockedAssetDaoGetByKeyCall(assetKey, null)
            .arrange()

        // When
        assetRepository.downloadPublicAsset(assetKey.value, assetKey.domain)

        // Then
        with(arrangement) {
            verify(assetDAO).suspendFunction(assetDAO::getAssetByKey)
                .with(eq(assetKey.value))
                .wasInvoked(exactly = once)
            verify(assetApi).suspendFunction(assetApi::downloadAsset)
                .with(matching { it == assetKey.value }, matching { it == assetKey.domain }, eq(null), any())
                .wasInvoked(exactly = once)
            verify(assetDAO)
                .suspendFunction(assetDAO::insertAsset)
                .with(any())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenAnAssetIdAndDownloadIfNeededSetToTrue_whenDownloadingPrivateAssetsAndNotPresentInDB_thenReturnItsPathFromRemoteAndPersistIt() =
        runTest {
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
        val result = assetRepository.fetchPrivateDecodedAsset(
            assetId = assetKey.value,
            assetDomain = assetKey.domain,
            assetName = assetName,
            assetToken = assetToken,
            encryptionKey = assetEncryptionKey,
            assetSHA256Key = SHA256Key(assetSha256!!),
            downloadIfNeeded = true,
            mimeType = null
        )

        // Then
        with(arrangement) {
            result.shouldSucceed()
            val expectedPath = fakeKaliumFileSystem.providePersistentAssetPath("${assetKey.value}.${assetName.fileExtension()}")
            val realPath = (result as Either.Right<Path>).value
            assertEquals(expectedPath, realPath)
            verify(assetDAO).suspendFunction(assetDAO::getAssetByKey)
                .with(eq(assetKey.value))
                .wasInvoked(exactly = once)
            verify(assetApi).suspendFunction(assetApi::downloadAsset)
                .with(matching { it == assetKey.value }, matching { it == assetKey.domain }, eq(assetToken), any())
                .wasInvoked(exactly = once)
            verify(assetDAO)
                .suspendFunction(assetDAO::insertAsset)
                .with(any())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenAnAssetIdAndDownloadIfNeededSetToFalse_whenDownloadingPrivateAssetsAndNotPresentInDB_thenReturnFailure() =
        runTest {
            // Given
            val assetKey = UserAssetId("value1", "domain1")
            val assetName = "Eiffel Tower.jpg"
            val assetToken = "some-token"
            val assetEncryptionKey = generateRandomAES256Key()
            val assetRawData = assetName.encodeToByteArray()
            val encryptedDataPath = encryptDataWithPath(assetRawData, assetEncryptionKey)
            val assetSha256 = calcFileSHA256(fakeKaliumFileSystem.source(encryptedDataPath))

            val (arrangement, assetRepository) = Arrangement()
                .withMockedAssetDaoGetByKeyCall(assetKey, null)
                .arrange()

            // When
            val result = assetRepository.fetchPrivateDecodedAsset(
                assetId = assetKey.value,
                assetDomain = assetKey.domain,
                assetName = assetName,
                assetToken = assetToken,
                encryptionKey = assetEncryptionKey,
                assetSHA256Key = SHA256Key(assetSha256!!),
                downloadIfNeeded = false,
                mimeType = null
            )

            // Then
            with(arrangement) {
                result.shouldFail()
                assertIs<StorageFailure.DataNotFound>(result.value)
                verify(assetDAO).suspendFunction(assetDAO::getAssetByKey)
                    .with(eq(assetKey.value))
                    .wasInvoked(exactly = once)
                verify(assetApi).suspendFunction(assetApi::downloadAsset)
                    .with(matching { it == assetKey.value }, matching { it == assetKey.domain }, eq(assetToken), any())
                    .wasNotInvoked()
                verify(assetDAO)
                    .suspendFunction(assetDAO::insertAsset)
                    .with(any())
                    .wasNotInvoked()
            }
        }

    @Test
    fun givenAnAssetId_whenDownloadingPrivateAssetsAndAlreadyPresentInDB_thenReturnItsPathFromLocal() =
        runTest {
            // Given
            val assetKey = UserAssetId("value1", "domain1")
            val assetName = "Eiffel Tower.jpg"
            val assetToken = "some-token"
            val assetEncryptionKey = generateRandomAES256Key()
            val assetRawData = assetName.encodeToByteArray()
            val encryptedDataPath = encryptDataWithPath(assetRawData, assetEncryptionKey)
            val assetSha256 = calcFileSHA256(fakeKaliumFileSystem.source(encryptedDataPath))
            val assetPath = fakeKaliumFileSystem.providePersistentAssetPath(assetName)

            val (arrangement, assetRepository) = Arrangement()
                .withMockedAssetDaoGetByKeyCall(assetKey, stubAssetEntity(assetKey.value, assetPath, assetRawData.size.toLong()))
                .arrange()

            // When
            val result = assetRepository.fetchPrivateDecodedAsset(
                assetId = assetKey.value,
                assetDomain = assetKey.domain,
                assetName = assetName,
                assetToken = assetToken,
                encryptionKey = assetEncryptionKey,
                assetSHA256Key = SHA256Key(assetSha256!!),
                downloadIfNeeded = false,
                mimeType = null
            )

            // Then
            with(arrangement) {
                result.shouldSucceed()
                val realPath = (result as Either.Right<Path>).value
                assertEquals(assetPath, realPath)
                verify(assetDAO).suspendFunction(assetDAO::getAssetByKey)
                    .with(eq(assetKey.value))
                    .wasInvoked(exactly = once)
                verify(assetApi).suspendFunction(assetApi::downloadAsset)
                    .with(matching { it == assetKey.value }, matching { it == assetKey.domain }, eq(assetToken), any())
                    .wasNotInvoked()
                verify(assetDAO)
                    .suspendFunction(assetDAO::insertAsset)
                    .with(any())
                    .wasNotInvoked()
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
            assetRepository.fetchPrivateDecodedAsset(
                assetKey.value,
                assetKey.domain,
                assetName,
                null,
                assetToken,
                assetEncryptionKey,
                SHA256Key(wrongAssetSha256!!)
            )

        // Then
        with(arrangement) {
            result.shouldFail()
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
        val actual = assetRepository.downloadPublicAsset(assetKey.value, assetKey.domain)

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
                .with(matching { it == assetKey.value }, matching { it == assetKey.domain }, eq(null), any())
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
        val actual = assetRepository.fetchPrivateDecodedAsset(
            assetId = assetKey.value,
            assetDomain = assetKey.domain,
            assetName,
            null,
            null,
            encryptionKey,
            assetSha256
        )

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
                .with(matching { it == assetKey.value }, matching { it == assetKey.domain }, eq(null), any())
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
        assetRepository.downloadPublicAsset(assetKey.value, assetKey.domain)

        // Then
        with(arrangement) {
            verify(assetDAO).suspendFunction(assetDAO::getAssetByKey)
                .with(eq(assetKey.value))
                .wasInvoked(exactly = once)

            verify(assetApi).suspendFunction(assetApi::downloadAsset)
                .with(matching { it == assetKey.value }, matching { it == assetKey.value }, eq(null), any())
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
        assetRepository.deleteAsset(assetKey.value, assetKey.domain, "asset-token")

        // Then
        verify(arrangement.assetApi).suspendFunction(arrangement.assetApi::deleteAsset)
            .with(any(), any(), any())
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
        assetRepository.deleteAsset(assetKey.value, assetKey.domain, "asset-token")

        // Then
        verify(arrangement.assetApi).suspendFunction(arrangement.assetApi::deleteAsset)
            .with(any(), any(), any())
            .wasInvoked(exactly = once)

        verify(arrangement.assetDAO).suspendFunction(arrangement.assetDAO::deleteAsset)
            .with(any())
            .wasInvoked(exactly = once)

    }

    @Test
    fun givenValidParams_whenPersistingAsset_thenShouldSucceedWithAPathResponse() = runTest {
        // Given
        val dataNamePath = "temp-data-path"
        val fullDataPath = fakeKaliumFileSystem.tempFilePath(dataNamePath)
        val dummyData = "some-dummy-data".toByteArray()
        val dataSize = dummyData.size.toLong()
        val assetId = "some_key"
        val assetDomain = "some_domain"
        val expectedAssetPath = fakeKaliumFileSystem.providePersistentAssetPath(assetId)

        val (arrangement, assetRepository) = Arrangement()
            .withRawStoredData(dummyData, fullDataPath)
            .withSuccessfulInsert()
            .arrange()

        // When
        val actual = assetRepository.persistAsset(
            assetId = assetId,
            assetDomain = assetDomain,
            assetDataSize = dataSize,
            decodedDataPath = fullDataPath,
            extension = null
        )

        // Then
        actual.shouldSucceed {
            assertIs<Path>(it)
            assertEquals(expectedAssetPath, it)
        }
        verify(arrangement.assetDAO).suspendFunction(arrangement.assetDAO::insertAsset)
            .with(matching {
                it.dataPath == expectedAssetPath.toString() && it.key == assetId && it.domain == assetDomain && it.dataSize == dataSize
            })
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAnError_whenPersistingAsset_thenShouldReturnFailure() = runTest {
        // Given
        val dataNamePath = "temp-data-path"
        val fullDataPath = fakeKaliumFileSystem.tempFilePath(dataNamePath)
        val dummyData = "some-dummy-data".toByteArray()
        val dataSize = dummyData.size.toLong()
        val assetId = "some_key"
        val assetDomain = "some_domain"
        val expectedAssetPath = fakeKaliumFileSystem.providePersistentAssetPath(assetId)

        val (arrangement, assetRepository) = Arrangement()
            .withRawStoredData(dummyData, fullDataPath)
            .withErrorInsertResponse()
            .arrange()

        // When
        val actual = assetRepository.persistAsset(
            assetId = assetId,
            assetDomain = assetDomain,
            assetDataSize = dataSize,
            decodedDataPath = fullDataPath,
            extension = null
        )

        // Then
        actual.shouldFail()
        verify(arrangement.assetDAO).suspendFunction(arrangement.assetDAO::insertAsset)
            .with(matching {
                it.dataPath == expectedAssetPath.toString() && it.key == assetId && it.domain == assetDomain && it.dataSize == dataSize
            })
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenDataFileNotExisting_whenPersistingAsset_thenShouldReturnFailure() = runTest {
        // Given
        val dataNamePath = "temp-data-path"
        val fullDataPath = fakeKaliumFileSystem.tempFilePath(dataNamePath)
        val dummyData = "some-dummy-data".toByteArray()
        val dataSize = dummyData.size.toLong()
        val assetId = "some_key"
        val assetDomain = "some_domain"

        val (arrangement, assetRepository) = Arrangement().arrange()

        // When
        val actual = assetRepository.persistAsset(
            assetId = assetId,
            assetDomain = assetDomain,
            assetDataSize = dataSize,
            decodedDataPath = fullDataPath,
            extension = null
        )

        // Then
        actual.shouldFail()
        verify(arrangement.assetDAO).suspendFunction(arrangement.assetDAO::insertAsset)
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

        fun withRawStoredData(data: ByteArray, dataPath: Path): Arrangement = apply {
            fakeKaliumFileSystem.sink(dataPath).buffer().use { it.write(data) }
        }

        fun withSuccessfulInsert(): Arrangement = apply {
            given(assetDAO)
                .suspendFunction(assetDAO::insertAsset)
                .whenInvokedWith(any())
                .thenDoNothing()
        }

        fun withErrorInsertResponse(): Arrangement = apply {
            given(assetDAO)
                .suspendFunction(assetDAO::insertAsset)
                .whenInvokedWith(any())
                .thenThrow(RuntimeException("An error occurred persisting the data"))
        }

        fun withSuccessfulUpload(expectedAssetResponse: AssetResponse): Arrangement = apply {
            given(assetApi)
                .suspendFunction(assetApi::uploadAsset)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(NetworkResponse.Success(expectedAssetResponse, mapOf(), 200))
            withSuccessfulInsert()
        }

        fun withSuccessfulDownload(assetsIdToPersist: List<AssetId>): Arrangement = apply {
            assetsIdToPersist.forEach { assetKey ->
                withMockedAssetDaoGetByKeyCall(assetKey, null)
                given(assetApi)
                    .suspendFunction(assetApi::downloadAsset)
                    .whenInvokedWith(any(), any(), any(), any())
                    .thenReturn(NetworkResponse.Success(Unit, mapOf(), 200))
                given(assetApi)
                    .suspendFunction(assetApi::downloadAsset)
                    .whenInvokedWith(any(), eq(null), any(), any())
                    .thenReturn(NetworkResponse.Success(Unit, mapOf(), 200))

                withSuccessfulInsert()
            }
        }

        fun withSuccessfulDownloadAndPersistedData(assetsIdToPersist: List<Pair<AssetId, ByteArray>>): Arrangement = apply {
            assetsIdToPersist.forEach { (assetKey, assetData) ->
                withMockedAssetDaoGetByKeyCall(assetKey, null)
                given(assetApi)
                    .suspendFunction(assetApi::downloadAsset)
                    .whenInvokedWith(any(), any(), any(), matching {
                        val buffer = Buffer()
                        buffer.write(assetData)
                        it.write(buffer, assetData.size.toLong())
                        true
                    })
                    .thenReturn(NetworkResponse.Success(Unit, mapOf(), 200))
                given(assetApi)
                    .suspendFunction(assetApi::downloadAsset)
                    .whenInvokedWith(any(), anything(), eq(null), matching {
                        val buffer = Buffer()
                        buffer.write(assetData)
                        it.write(buffer, assetData.size.toLong())
                        true
                    })
                    .thenReturn(NetworkResponse.Success(Unit, mapOf(), 200))

                withSuccessfulInsert()
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
                .whenInvokedWith(anything(), anything(), anything(), anything())
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
                .whenInvokedWith(anything(), anything(), anything())
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
                .whenInvokedWith(anything(), anything(), anything())
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
