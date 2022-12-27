package com.wire.kalium.logic.data.asset

import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.cryptography.utils.calcFileSHA256
import com.wire.kalium.cryptography.utils.decryptFileWithAES256
import com.wire.kalium.cryptography.utils.encryptFileWithAES256
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.EncryptionFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.fileExtension
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.base.authenticated.asset.AssetApi
import com.wire.kalium.persistence.dao.asset.AssetDAO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink

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
     * @param assetDataPath the [AES256Key] that will be used to encrypt the data living in [assetDataPath]
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
     * @return [Either] a [CoreFailure] if anything went wrong, or the [Path] to the decoded asset
     */
    @Suppress("LongParameterList")
    suspend fun fetchPrivateDecodedAsset(
        assetId: String,
        assetDomain: String?,
        assetName: String,
        assetToken: String?,
        encryptionKey: AES256Key,
        assetSHA256Key: SHA256Key
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
}

internal class AssetDataSource(
    private val assetApi: AssetApi,
    private val assetDao: AssetDAO,
    private val assetMapper: AssetMapper = MapperProvider.assetMapper(),
    private val kaliumFileSystem: KaliumFileSystem,
    private val idMapper: IdMapper = MapperProvider.idMapper()
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

        val encryptionSucceeded = (encryptedDataSize > 0L && sha256 != null)

        return if (encryptionSucceeded) {
            val uploadAssetData = UploadAssetData(tempEncryptedDataPath, encryptedDataSize, mimeType, false, RetentionType.PERSISTENT)
            uploadAndPersistAsset(uploadAssetData, assetDataPath, extension).map { it to SHA256Key(sha256!!) }
        } else {
            kaliumLogger.e("Something went wrong when encrypting the Asset Message")
            Either.Left(EncryptionFailure.GenericEncryptionError)
        }
    }

    private suspend fun uploadAndPersistAsset(
        uploadAssetData: UploadAssetData,
        decodedDataPath: Path,
        extension: String?
    ): Either<CoreFailure, UploadedAssetId> =
        assetMapper.toMetadataApiModel(uploadAssetData, kaliumFileSystem).let { metaData ->
            wrapApiRequest {
                val dataSource = {
                    kaliumFileSystem.source(uploadAssetData.tempEncryptedDataPath)
                }

                // we should also consider for avatar images, the compression for preview vs complete picture
                assetApi.uploadAsset(metaData, dataSource, uploadAssetData.dataSize)
            }
        }.flatMap { assetResponse ->
            // After successful upload, we persist the asset to a persistent path
            val persistentAssetDataPath =
                kaliumFileSystem.providePersistentAssetPath(assetName = buildFileName(assetResponse.key, extension))

            // After successful upload we finally persist the data now to a persistent path and delete the temporary one
            kaliumFileSystem.copy(decodedDataPath, persistentAssetDataPath)
            kaliumFileSystem.delete(decodedDataPath)
            kaliumFileSystem.delete(uploadAssetData.tempEncryptedDataPath)

            assetMapper.fromUploadedAssetToDaoModel(uploadAssetData, assetResponse).let { assetEntity ->
                // We need to update the persistent asset path with the decoded one
                val decodedAssetEntity = assetEntity.copy(dataPath = persistentAssetDataPath.toString())

                wrapStorageRequest { assetDao.insertAsset(decodedAssetEntity) }
            }.map { assetMapper.fromApiUploadResponseToDomainModel(assetResponse) }
        }

    override suspend fun downloadPublicAsset(assetId: String, assetDomain: String?): Either<CoreFailure, Path> =
        fetchOrDownloadDecodedAsset(assetId = assetId, assetDomain = assetDomain, assetName = assetId.toString(), assetToken = null)

    override suspend fun fetchPrivateDecodedAsset(
        assetId: String,
        assetDomain: String?,
        assetName: String,
        assetToken: String?,
        encryptionKey: AES256Key,
        assetSHA256Key: SHA256Key
    ): Either<CoreFailure, Path> =
        fetchOrDownloadDecodedAsset(
            assetId = assetId,
            assetDomain,
            assetName = assetName,
            assetToken = assetToken,
            encryptionKey = encryptionKey,
            assetSHA256 = assetSHA256Key
        )

    @Suppress("LongParameterList")
    private suspend fun fetchOrDownloadDecodedAsset(
        assetId: String,
        assetDomain: String?,
        assetName: String,
        assetToken: String?,
        encryptionKey: AES256Key? = null,
        assetSHA256: SHA256Key? = null
    ): Either<CoreFailure, Path> {
        return wrapStorageRequest { assetDao.getAssetByKey(assetId).firstOrNull() }.fold({
            val tempFile = kaliumFileSystem.tempFilePath("temp_$assetId")
            val tempFileSink = kaliumFileSystem.sink(tempFile)
            wrapApiRequest {
                // Backend sends asset messages with empty asset tokens
                assetApi.downloadAsset(assetId, assetDomain, assetToken?.ifEmpty { null }, tempFileSink)
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
                        kaliumFileSystem.providePersistentAssetPath(buildFileName(assetId, assetName.fileExtension()))

                    val decodedAssetSink = kaliumFileSystem.sink(decodedAssetPath)

                    // Public assets are stored already decrypted on the backend, hence no decryption is needed
                    val (hashError, assetDataSize) = decodeAssetIfNeeded(tempFile, encryptionKeys, decodedAssetSink)

                    // Delete temp path now that the decoded asset has been persisted correctly
                    kaliumFileSystem.delete(tempFile)

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
        }, {
            Either.Right(it.dataPath.toPath())
        })
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
        wrapStorageRequest { assetDao.deleteAsset(assetId) }
}

private fun buildFileName(name: String, extension: String?): String =
    extension?.let { "$name.$extension" } ?: name
