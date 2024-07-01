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
import com.wire.kalium.network.api.authenticated.asset.AssetResponse
import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.asset.AssetDAO
import com.wire.kalium.persistence.dao.asset.AssetEntity
import io.ktor.utils.io.core.toByteArray
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
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

        coVerify {
            arrangement.assetApi.uploadAsset(any(), any(), any())
        }.wasInvoked(exactly = once)
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

        coVerify {
            arrangement.assetApi.uploadAsset(any(), any(), any())
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.assetDAO.insertAsset(any())
        }.wasInvoked(exactly = once)
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

        coVerify {
            arrangement.assetApi.uploadAsset(any(), any(), any())
        }.wasInvoked(exactly = once)
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

        coVerify {
            arrangement.assetApi.uploadAsset(any(), any(), any())
        }.wasInvoked(exactly = once)
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
            coVerify {
                assetDAO.getAssetByKey(eq(assetKey.value))
            }.wasInvoked(exactly = once)
            coVerify {
                assetApi.downloadAsset(
                    matches { it == assetKey.value },
                    matches { it == assetKey.domain },
                    eq<String?>(null),
                    any()
                )
            }.wasInvoked(exactly = once)
            coVerify {
                assetDAO.insertAsset(any())
            }.wasInvoked(exactly = once)
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
                coVerify {
                    assetDAO.getAssetByKey(eq(assetKey.value))
                }.wasInvoked(exactly = once)
                coVerify {
                    assetApi.downloadAsset(matches { it == assetKey.value }, matches { it == assetKey.domain }, eq(assetToken), any())
                }.wasInvoked(exactly = once)
                coVerify {
                    assetDAO.insertAsset(any())
                }.wasInvoked(exactly = once)
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
                coVerify {
                    assetDAO.getAssetByKey(eq(assetKey.value))
                }.wasInvoked(exactly = once)
                coVerify {
                    assetApi.downloadAsset(matches { it == assetKey.value }, matches { it == assetKey.domain }, eq(assetToken), any())
                }.wasNotInvoked()
                coVerify {
                    assetDAO.insertAsset(any())
                }.wasNotInvoked()
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
                coVerify {
                    assetDAO.getAssetByKey(eq(assetKey.value))
                }.wasInvoked(exactly = once)
                coVerify {
                    assetApi.downloadAsset(matches { it == assetKey.value }, matches { it == assetKey.domain }, eq(assetToken), any())
                }.wasNotInvoked()
                coVerify {
                    assetDAO.insertAsset(any())
                }.wasNotInvoked()
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

        with(arrangement) {
            coVerify {
                assetDAO.getAssetByKey(matches { it == assetKey.value })
            }.wasInvoked(exactly = once)
            coVerify {
                assetApi.downloadAsset(
                    eq(assetKey.value),
                    eq(assetKey.domain),
                    eq<String?>(null),
                    any()
                )
            }.wasInvoked(exactly = once)
            coVerify {
                assetDAO.insertAsset(any())
            }.wasNotInvoked()
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

        with(arrangement) {
            coVerify {
                assetDAO.getAssetByKey(matches { it == assetKey.value })
            }.wasInvoked(exactly = once)
            coVerify {
                assetApi.downloadAsset(
                    matches { it == assetKey.value },
                    matches { it == assetKey.domain },
                    eq<String?>(null),
                    any()
                )
            }.wasInvoked(exactly = once)
            coVerify {
                assetDAO.insertAsset(any())
            }.wasNotInvoked()
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
            coVerify {
                assetDAO.getAssetByKey(eq(assetKey.value))
            }.wasInvoked(exactly = once)

            coVerify {
                assetApi.downloadAsset(
                    matches { it == assetKey.value },
                    matches { it == assetKey.value },
                    matches { it == null },
                    any()
                )
            }.wasNotInvoked()
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
        coVerify {
            arrangement.assetApi.deleteAsset(any(), any(), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.assetDAO.deleteAsset(any())
        }.wasNotInvoked()
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
        coVerify {
            arrangement.assetApi.deleteAsset(any(), any(), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.assetDAO.deleteAsset(any())
        }.wasInvoked(exactly = once)

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
        coVerify {
            arrangement.assetDAO.insertAsset(
                matches {
                    it.dataPath == expectedAssetPath.toString() && it.key == assetId && it.domain == assetDomain && it.dataSize == dataSize
                }
            )
        }.wasInvoked(exactly = once)
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
        coVerify {
            arrangement.assetDAO.insertAsset(
                matches {
                    it.dataPath == expectedAssetPath.toString() && it.key == assetId && it.domain == assetDomain && it.dataSize == dataSize
                }
            )
        }.wasInvoked(exactly = once)
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
        coVerify {
            arrangement.assetDAO.insertAsset(any())
        }.wasNotInvoked()
    }

    class Arrangement {

        @Mock
        val assetApi = mock(AssetApi::class)

        @Mock
        val assetDAO = mock(AssetDAO::class)

        private val assetMapper by lazy { AssetMapperImpl() }

        private val assetRepository = AssetDataSource(assetApi, assetDAO, assetMapper, fakeKaliumFileSystem)

        fun withRawStoredData(data: ByteArray, dataPath: Path): Arrangement = apply {
            fakeKaliumFileSystem.sink(dataPath).buffer().use { it.write(data) }
        }

        suspend fun withSuccessfulInsert(): Arrangement = apply {
            coEvery {
                assetDAO.insertAsset(any())
            }.returns(Unit)
        }

        suspend fun withErrorInsertResponse(): Arrangement = apply {
            coEvery {
                assetDAO.insertAsset(any())
            }.throws(RuntimeException("An error occurred persisting the data"))
        }

        suspend fun withSuccessfulUpload(expectedAssetResponse: AssetResponse): Arrangement = apply {
            coEvery {
                assetApi.uploadAsset(any(), any(), any())
            }.returns(NetworkResponse.Success(expectedAssetResponse, mapOf(), 200))
            withSuccessfulInsert()
        }

        suspend fun withSuccessfulDownload(assetsIdToPersist: List<AssetId>): Arrangement = apply {
            assetsIdToPersist.forEach { assetKey ->
                withMockedAssetDaoGetByKeyCall(assetKey, null)
                coEvery {
                    assetApi.downloadAsset(any(), any(), any(), any())
                }.returns(NetworkResponse.Success(Unit, mapOf(), 200))
//                 coEvery {
//                     assetApi.downloadAsset(any(), eq<String?>(null), any(), any())
//                 }.returns(NetworkResponse.Success(Unit, mapOf(), 200))

                withSuccessfulInsert()
            }
        }

        suspend fun withSuccessfulDownloadAndPersistedData(assetsIdToPersist: List<Pair<AssetId, ByteArray>>): Arrangement = apply {
            assetsIdToPersist.forEach { (assetKey, assetData) ->
                withMockedAssetDaoGetByKeyCall(assetKey, null)
                coEvery {
                    assetApi.downloadAsset(
                        any(), any(), any(),
                        matches {
                            val buffer = Buffer()
                            buffer.write(assetData)
                            it.write(buffer, assetData.size.toLong())
                            true
                        }
                    )
                }.returns(NetworkResponse.Success(Unit, mapOf(), 200))
                coEvery {
                    assetApi.downloadAsset(
                        any(), any(), matches { it == null },
                        matches {
                            val buffer = Buffer()
                            buffer.write(assetData)
                            it.write(buffer, assetData.size.toLong())
                            true
                        }
                    )
                }.returns(NetworkResponse.Success(Unit, mapOf(), 200))

                withSuccessfulInsert()
            }
        }

        suspend fun withErrorUploadResponse(): Arrangement = apply {
            coEvery {
                assetApi.uploadAsset(any(), any(), any())
            }.returns(
                NetworkResponse.Error(
                    KaliumException.ServerError(
                        ErrorResponse(500, "error_message", "error_label")
                    )
                )
            )
        }

        suspend fun withErrorDownloadResponse(): Arrangement = apply {
            coEvery {
                assetApi.downloadAsset(any(), any(), any(), any())
            }.returns(
                NetworkResponse.Error(
                    KaliumException.ServerError(
                        ErrorResponse(400, "error_message", "error_label")
                    )
                )
            )
        }

        suspend fun withMockedAssetDaoGetByKeyCall(assetKey: UserAssetId, expectedAssetEntity: AssetEntity?): Arrangement = apply {
            coEvery {
                assetDAO.getAssetByKey(eq(assetKey.value))
            }.returns(flowOf(expectedAssetEntity))
        }

        suspend fun withErrorDeleteResponse(): Arrangement = apply {
            coEvery {
                assetApi.deleteAsset(any(), any(), any())
            }.returns(
                NetworkResponse.Error(
                    KaliumException.ServerError(
                        ErrorResponse(500, "error_message", "error_label")
                    )
                )
            )
        }

        suspend fun withSuccessDeleteRemotelyResponse(): Arrangement = apply {
            coEvery {
                assetApi.deleteAsset(any(), any(), any())
            }.returns(NetworkResponse.Success(Unit, mapOf(), 200))
        }

        suspend fun withSuccessDeleteLocallyResponse(): Arrangement = apply {
            coEvery {
                assetDAO.deleteAsset(any())
            }.returns(Unit)
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
