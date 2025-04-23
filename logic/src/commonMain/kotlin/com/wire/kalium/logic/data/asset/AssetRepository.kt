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
import com.wire.kalium.cryptography.utils.decryptFileWithAES256
import com.wire.kalium.cryptography.utils.encryptFileWithAES256
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.EncryptionFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.util.fileExtension
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.network.api.base.authenticated.asset.AssetApi
import com.wire.kalium.persistence.dao.asset.AssetDAO
import com.wire.kalium.util.getExtensionFromMimeType
import io.mockative.Mockable
import kotlinx.coroutines.flow.firstOrNull
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink

@Mockable
interface AssetRepository {
    /**
     * Method used to upload and persist to local memory a public asset
     * @param mimeType type of the asset to be uploaded
     * @param assetDataPath the path of the unencrypted data to be uploaded
     * @param assetDataSize the size of the unencrypted data to be uploaded
     * @return [Either] a [CoreFailure] if anything went wrong, or the [UploadedAssetId] of the asset if successful
     */
    suspend fun uploadAndPersistPublicAsset(
        mimeType: String,
        assetDataPath: Path,
        assetDataSize: Long
    ): Either<CoreFailure, UploadedAssetId>

    /**
     * Method used to upload the encrypted data and persist to local memory the already decoded asset
     * @param mimeType type of the asset to be uploaded
     * @param assetDataPath the path of the encrypted data to be uploaded
     * @param otrKey the [AES256Key] that will be used to encrypt the data living in [assetDataPath]
     * @param extension extension of the asset to be uploaded
     * @return [Either] a [CoreFailure] if anything went wrong, or the [UploadedAssetId] of the newly created asset and the [SHA256Key] of
     * the encrypted asset if successful
     */
    suspend fun uploadAndPersistPrivateAsset(
        mimeType: String,
        assetDataPath: Path,
        otrKey: AES256Key,
        extension: String?
    ): Either<CoreFailure, Pair<UploadedAssetId, SHA256Key>>

    /**
     * Method used to persist to local memory the decoded asset
     * @param assetId key of the asset to be persisted
     * @param assetDomain domain of the asset to be persisted
     * @param decodedDataPath  the path of the unencrypted data to be persisted
     * @param assetDataSize the size of the unencrypted data to be persisted
     * @param extension extension of the asset to be persisted
     * @return [Either] a [CoreFailure] if anything went wrong, or the [Path] of the persisted asset
     */
    suspend fun persistAsset(
        assetId: String,
        assetDomain: String?,
        decodedDataPath: Path,
        assetDataSize: Long,
        extension: String?
    ): Either<CoreFailure, Path>

    /**
     * Method used to download and persist to local memory a public asset
     * @param assetId the asset identifier
     * @return [Either] a [CoreFailure] if anything went wrong, or the [Path] of the decoded asset
     */
    suspend fun downloadPublicAsset(assetId: String, assetDomain: String?): Either<CoreFailure, Path>

    /**
     * Method used to fetch the [Path] of a decoded private asset
     * @param assetId the asset identifier
     * @param assetName the name of the original asset
     * @param assetToken the asset token used to provide an extra layer of asset/user authentication
     * @param encryptionKey the asset encryption key used to decrypt an extra layer of asset/user authentication
     * @param downloadIfNeeded flag determining whether it should make a request do download an asset if it's not available locally
     * @return [Either] a [CoreFailure] if anything went wrong, or the [Path] to the decoded asset
     */
    @Suppress("LongParameterList")
    suspend fun fetchPrivateDecodedAsset(
        assetId: String,
        assetDomain: String?,
        assetName: String,
        mimeType: String?,
        assetToken: String?,
        encryptionKey: AES256Key,
        assetSHA256Key: SHA256Key,
        downloadIfNeeded: Boolean = true
    ): Either<CoreFailure, Path>

    /**
     * Method used to delete asset locally and externally
     */
    suspend fun deleteAsset(assetId: String, assetDomain: String?, assetToken: String?): Either<CoreFailure, Unit>

    /**
     * Method used to delete asset only locally
     */
    // TODO(federation): add the domain to delete asset locally
    suspend fun deleteAssetLocally(assetId: String): Either<CoreFailure, Unit>
    suspend fun fetchDecodedAsset(assetId: String): Either<CoreFailure, Path>
}

@Suppress("TooManyFunctions")
internal class AssetDataSource(
    private val assetApi: AssetApi,
    private val assetDao: AssetDAO,
    private val assetMapper: AssetMapper = MapperProvider.assetMapper(),
    private val kaliumFileSystem: KaliumFileSystem
) : AssetRepository {

    override suspend fun uploadAndPersistPublicAsset(
        mimeType: String,
        assetDataPath: Path,
        assetDataSize: Long
    ): Either<CoreFailure, UploadedAssetId> {
        val uploadAssetData = UploadAssetData(assetDataPath, assetDataSize, mimeType, true, RetentionType.ETERNAL)
        return uploadAndPersistAsset(uploadAssetData, assetDataPath, null)
    }

    override suspend fun uploadAndPersistPrivateAsset(
        mimeType: String,
        assetDataPath: Path,
        otrKey: AES256Key,
        extension: String?
    ): Either<CoreFailure, Pair<UploadedAssetId, SHA256Key>> {
        try {
            val tempEncryptedDataPath = kaliumFileSystem.tempFilePath("${assetDataPath.name}.aes")
            val assetDataSource = kaliumFileSystem.source(assetDataPath)
            val assetDataSink = kaliumFileSystem.sink(tempEncryptedDataPath)

            // Encrypt the data on the provided temp path
            val encryptedDataSize = encryptFileWithAES256(assetDataSource, otrKey, assetDataSink)
            val encryptedDataSource = kaliumFileSystem.source(tempEncryptedDataPath)

            // Calculate the SHA of the encrypted data
            val sha256 = calcFileSHA256(encryptedDataSource)
            assetDataSink.close()
            encryptedDataSource.close()
            assetDataSource.close()

            val encryptionSucceeded = (encryptedDataSize > 0L && sha256 != null)

            return if (encryptionSucceeded) {
                val uploadAssetData = UploadAssetData(tempEncryptedDataPath, encryptedDataSize, mimeType, false, RetentionType.PERSISTENT)
                uploadAndPersistAsset(uploadAssetData, assetDataPath, extension).map { it to SHA256Key(sha256!!) }
            } else {
                kaliumLogger.e("Something went wrong when encrypting the Asset Message")
                Either.Left(EncryptionFailure.GenericEncryptionError)
            }
        } catch (e: IOException) {
            kaliumLogger.e("Something went wrong when uploading the Asset Message. $e")
            return Either.Left(CoreFailure.Unknown(e))
        }
    }

    private suspend fun uploadAndPersistAsset(
        uploadAssetData: UploadAssetData,
        decodedDataPath: Path,
        extension: String?
    ): Either<CoreFailure, UploadedAssetId> =
        assetMapper.toMetadataApiModel(uploadAssetData, kaliumFileSystem).let { metaData ->
            wrapApiRequest {
                val dataSource = kaliumFileSystem.source(uploadAssetData.tempEncryptedDataPath)

                // we should also consider for avatar images, the compression for preview vs complete picture
                assetApi.uploadAsset(metaData, { dataSource }, uploadAssetData.dataSize)
                    .also { dataSource.close() }
            }
        }
            .flatMap { assetResponse ->
                // After successful upload, we persist the asset to a persistent path
                persistAsset(assetResponse.key, assetResponse.domain, decodedDataPath, uploadAssetData.dataSize, extension)
                    .also { kaliumFileSystem.delete(uploadAssetData.tempEncryptedDataPath) }
                    .map { assetMapper.fromApiUploadResponseToDomainModel(assetResponse) }
            }

    override suspend fun persistAsset(
        assetId: String,
        assetDomain: String?,
        decodedDataPath: Path,
        assetDataSize: Long,
        extension: String?
    ): Either<CoreFailure, Path> {
        return wrapStorageRequest {
            val persistentAssetDataPath = kaliumFileSystem.providePersistentAssetPath(assetName = buildFileName(assetId, extension))
            kaliumFileSystem.copy(decodedDataPath, persistentAssetDataPath)
            kaliumFileSystem.delete(decodedDataPath)
            val decodedAssetEntity = assetMapper.fromUserAssetToDaoModel(assetId, assetDomain, persistentAssetDataPath, assetDataSize)
            assetDao.insertAsset(decodedAssetEntity)
            return@wrapStorageRequest persistentAssetDataPath
        }
    }

    override suspend fun downloadPublicAsset(assetId: String, assetDomain: String?): Either<CoreFailure, Path> =
        fetchOrDownloadDecodedAsset(assetId = assetId, assetDomain = assetDomain, assetName = assetId, assetToken = null, mimeType = null)

    override suspend fun fetchPrivateDecodedAsset(
        assetId: String,
        assetDomain: String?,
        assetName: String,
        mimeType: String?,
        assetToken: String?,
        encryptionKey: AES256Key,
        assetSHA256Key: SHA256Key,
        downloadIfNeeded: Boolean
    ): Either<CoreFailure, Path> =
        if (!downloadIfNeeded) fetchDecodedAsset(assetId = assetId)
        else fetchOrDownloadDecodedAsset(
            assetId = assetId,
            assetDomain = assetDomain,
            assetName = assetName,
            mimeType = mimeType,
            assetToken = assetToken,
            encryptionKey = encryptionKey,
            assetSHA256 = assetSHA256Key
        )

    override suspend fun fetchDecodedAsset(assetId: String): Either<CoreFailure, Path> =
        wrapStorageRequest { assetDao.getAssetByKey(assetId).firstOrNull() }
            .map { it.dataPath.toPath() }

    @Suppress("LongParameterList")
    private suspend fun fetchOrDownloadDecodedAsset(
        assetId: String,
        assetDomain: String?,
        assetName: String,
        assetToken: String?,
        mimeType: String?,
        encryptionKey: AES256Key? = null,
        assetSHA256: SHA256Key? = null
    ): Either<CoreFailure, Path> =
        fetchDecodedAsset(assetId).flatMapLeft {
            val tempFile = kaliumFileSystem.tempFilePath("temp_$assetId")
            val tempFileSink = kaliumFileSystem.sink(tempFile)
            wrapApiRequest {
                // Backend sends asset messages with empty asset tokens
                assetApi.downloadAsset(assetId, assetDomain, assetToken?.ifEmpty { null }, tempFileSink).also {
                    tempFileSink.close()
                }
            }.flatMap {
                try {
                    if (encryptionKey != null && assetSHA256 == null) return@flatMap Either.Left(EncryptionFailure.WrongAssetHash)
                    val encryptionKeys = encryptionKey?.let { aes256Key ->
                        assetSHA256?.let { assetSHA256 ->
                            aes256Key to assetSHA256
                        } ?: return@flatMap Either.Left(EncryptionFailure.WrongAssetHash)
                    }

                    // Decrypt and persist decoded asset onto a persistent asset path
                    val decodedAssetPath =
                        kaliumFileSystem.providePersistentAssetPath(
                            buildFileName(
                                assetId, assetName.fileExtension()
                                    ?: getExtensionFromMimeType(mimeType)
                            )
                        )

                    val decodedAssetSink = kaliumFileSystem.sink(decodedAssetPath)

                    // Public assets are stored already decrypted on the backend, hence no decryption is needed
                    val (hashError, assetDataSize) = decodeAssetIfNeeded(tempFile, encryptionKeys, decodedAssetSink)

                    // Delete temp path now that the decoded asset has been persisted correctly
                    kaliumFileSystem.delete(tempFile)
                    decodedAssetSink.close()

                    when {
                        // Either a decryption error or a hash error occurred
                        hashError != null -> Either.Left(hashError)
                        assetDataSize <= 0L -> Either.Left(EncryptionFailure.GenericDecryptionError)

                        // Everything went fine, we persist the asset to the DB
                        else -> {
                            saveAssetInDB(assetId, assetDomain, decodedAssetPath, assetDataSize)
                            Either.Right(decodedAssetPath)
                        }
                    }
                } catch (e: IOException) {
                    kaliumLogger.e("Something went wrong when handling the Asset paths on the file system", e)
                    Either.Left(StorageFailure.DataNotFound)
                }
            }
        }

    private suspend fun decodeAssetIfNeeded(
        assetDataPath: Path,
        encryptionKeys: Pair<AES256Key, SHA256Key>?,
        decodedAssetSink: Sink
    ): Pair<EncryptionFailure?, Long> = with(kaliumFileSystem) {
        if (encryptionKeys != null) {
            val (encryptionKey, assetSHA256Key) = encryptionKeys
            validateAssetHashes(assetDataPath, assetSHA256Key).fold({ it to 0L }, {
                val assetDataSource = source(assetDataPath)
                val decryptedDataSize = decryptFileWithAES256(assetDataSource, decodedAssetSink, encryptionKey).also {
                    assetDataSource.close()
                }
                null to decryptedDataSize
            })
        } else {
            // Public assets are stored already decrypted on the backend, hence no decryption nor hash validation is needed
            val assetDataSource = source(assetDataPath)
            null to writeData(decodedAssetSink, assetDataSource).also { assetDataSource.close() }
        }
    }

    private fun validateAssetHashes(
        encryptedAssetDataPath: Path,
        storedAssetSha256Key: SHA256Key
    ): Either<EncryptionFailure, Unit> {
        // We open and close the source here to avoid keeping the file open for too long
        val encryptedAssetDataSource = kaliumFileSystem.source(encryptedAssetDataPath)
        val result = calcFileSHA256(encryptedAssetDataSource)?.let { downloadedAssetSha256Key ->
            if (downloadedAssetSha256Key.contentEquals(storedAssetSha256Key.data)) Either.Right(Unit)
            else Either.Left(EncryptionFailure.WrongAssetHash)
        } ?: Either.Left(EncryptionFailure.WrongAssetHash)

        encryptedAssetDataSource.close()
        return result
    }

    private suspend fun saveAssetInDB(
        assetId: String,
        assetDomain: String?,
        decodedAssetPath: Path,
        assetDataSize: Long
    ) = wrapStorageRequest {
        assetDao.insertAsset(
            assetMapper.fromUserAssetToDaoModel(
                assetId,
                assetDomain,
                decodedAssetPath,
                assetDataSize
            )
        )
    }

    override suspend fun deleteAsset(assetId: String, assetDomain: String?, assetToken: String?): Either<CoreFailure, Unit> =
        wrapApiRequest { assetApi.deleteAsset(assetId, assetDomain, assetToken) }
            .flatMap { deleteAssetLocally(assetId) }

    override suspend fun deleteAssetLocally(assetId: String): Either<CoreFailure, Unit> =
        deleteAssetFileLocally(assetId).let {
            wrapStorageRequest {
                assetDao.deleteAsset(assetId)
            }
        }

    private suspend fun deleteAssetFileLocally(assetId: String) {
        wrapStorageRequest {
            assetDao.getAssetByKey(assetId).firstOrNull()
        }.map {
            val filePath = it.dataPath.toPath()
            if (kaliumFileSystem.exists(filePath)) {
                kaliumFileSystem.delete(path = it.dataPath.toPath(), mustExist = false)
            }
        }
    }
}

private fun buildFileName(name: String, extension: String?): String =
    extension?.let { "$name.$extension" } ?: name
