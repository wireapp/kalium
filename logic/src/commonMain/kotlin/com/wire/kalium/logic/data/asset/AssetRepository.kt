package com.wire.kalium.logic.data.asset

import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.cryptography.utils.calcFileSHA256
import com.wire.kalium.cryptography.utils.decryptFileWithAES256
import com.wire.kalium.cryptography.utils.encryptFileWithAES256
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.EncryptionFailure
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.AssetId
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.fileExtension
import com.wire.kalium.logic.util.fileExtensionToAssetType
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.asset.AssetApi
import com.wire.kalium.persistence.dao.asset.AssetDAO
import kotlinx.coroutines.flow.firstOrNull
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import com.wire.kalium.network.api.AssetId as NetworkAssetId

interface AssetRepository {
    /**
     * Method used to upload and persist to local memory a public asset
     * @param mimeType type of the asset to be uploaded
     * @param assetDataPath the path of the unencrypted data to be uploaded
     * @param assetDataSize the size of the unencrypted data to be uploaded
     * @return [Either] a [CoreFailure] if anything went wrong, or the [UploadedAssetId] of the asset if successful
     */
    suspend fun uploadAndPersistPublicAsset(
        mimeType: AssetType,
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
        mimeType: AssetType,
        assetDataPath: Path,
        otrKey: AES256Key
    ): Either<CoreFailure, Pair<UploadedAssetId, SHA256Key>>

    /**
     * Method used to download and persist to local memory a public asset
     * @param assetId the asset identifier
     * @return [Either] a [CoreFailure] if anything went wrong, or the [Path] of the decoded asset
     */
    suspend fun downloadPublicAsset(assetId: AssetId): Either<CoreFailure, Path>

    /**
     * Method used to fetch the [Path] of a decoded private asset
     * @param assetId the asset identifier
     * @param assetName the name of the original asset
     * @param assetToken the asset token used to provide an extra layer of asset/user authentication
     * @param encryptionKey the asset encryption key used to decrypt an extra layer of asset/user authentication
     * @return [Either] a [CoreFailure] if anything went wrong, or the [Path] to the decoded asset
     */
    suspend fun fetchPrivateDecodedAsset(
        assetId: AssetId,
        assetName: String,
        assetToken: String?,
        encryptionKey: AES256Key
    ): Either<CoreFailure, Path>

    /**
     * Method used to download the list of avatar pictures of the current authenticated user
     * @param assetIdList list of the assets' id that wants to be downloaded
     * @return [Either] a [CoreFailure] if anything went wrong, or Unit if operation was successful
     */
    suspend fun downloadUsersPictureAssets(assetIdList: List<UserAssetId?>): Either<CoreFailure, Unit>

    /**
     * Method used to delete asset locally and externally
     */
    suspend fun deleteAsset(assetId: AssetId, assetToken: String?): Either<CoreFailure, Unit>

    /**
     * Method used to delete asset only locally
     */
    suspend fun deleteAssetLocally(assetId: AssetId): Either<CoreFailure, Unit>
}

internal class AssetDataSource(
    private val assetApi: AssetApi,
    private val assetDao: AssetDAO,
    private val assetMapper: AssetMapper = MapperProvider.assetMapper(),
    private val kaliumFileSystem: KaliumFileSystem,
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : AssetRepository {

    override suspend fun uploadAndPersistPublicAsset(
        mimeType: AssetType,
        assetDataPath: Path,
        assetDataSize: Long
    ): Either<CoreFailure, UploadedAssetId> {
        val uploadAssetData = UploadAssetData(assetDataPath, assetDataSize, mimeType, true, RetentionType.ETERNAL)
        return uploadAndPersistAsset(uploadAssetData, assetDataPath)
    }

    override suspend fun uploadAndPersistPrivateAsset(
        mimeType: AssetType,
        assetDataPath: Path,
        otrKey: AES256Key
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
            uploadAndPersistAsset(uploadAssetData, assetDataPath).map { it to SHA256Key(sha256!!) }
        } else {
            kaliumLogger.e("Something went wrong when encrypting the Asset Message")
            Either.Left(EncryptionFailure())
        }
    }

    private suspend fun uploadAndPersistAsset(
        uploadAssetData: UploadAssetData,
        decodedDataPath: Path
    ): Either<CoreFailure, UploadedAssetId> =
        assetMapper.toMetadataApiModel(uploadAssetData, kaliumFileSystem).let { metaData ->
            wrapApiRequest {
                val dataSource = kaliumFileSystem.source(uploadAssetData.tempEncryptedDataPath)

                // we should also consider for avatar images, the compression for preview vs complete picture
                assetApi.uploadAsset(metaData, dataSource, uploadAssetData.dataSize)
            }
        }.flatMap { assetResponse ->
            // After successful upload, we persist the asset to a persistent path
            val persistentAssetDataPath = kaliumFileSystem.providePersistentAssetPath(assetName = assetResponse.key)

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

    override suspend fun downloadPublicAsset(assetId: AssetId): Either<CoreFailure, Path> =
        fetchOrDownloadDecodedAsset(assetId = idMapper.toApiModel(assetId), assetName = "user_avatar_image.jpg", assetToken = null)

    override suspend fun fetchPrivateDecodedAsset(
        assetId: AssetId,
        assetName: String,
        assetToken: String?,
        encryptionKey: AES256Key
    ): Either<CoreFailure, Path> =
        fetchOrDownloadDecodedAsset(
            assetId = idMapper.toApiModel(assetId),
            assetName = assetName,
            assetToken = assetToken,
            encryptionKey = encryptionKey
        )

    private suspend fun fetchOrDownloadDecodedAsset(
        assetId: NetworkAssetId,
        assetName: String,
        assetToken: String?,
        encryptionKey: AES256Key? = null
    ): Either<CoreFailure, Path> =
        wrapStorageRequest { assetDao.getAssetByKey(assetId.value).firstOrNull() }.fold({
            wrapApiRequest {
                // Backend sends asset messages with empty asset tokens
                assetApi.downloadAsset(assetId, assetToken?.ifEmpty { null })
            }.flatMap { assetData ->
                // Copy byte array to temp file and provide it as source
                val tempFile = kaliumFileSystem.tempFilePath()
                val tempFileSink = kaliumFileSystem.sink(tempFile)
                tempFileSink.buffer().use {
                    it.write(assetData)
                }

                val encryptedAssetDataSource = kaliumFileSystem.source(tempFile)

                // Decrypt and persist decoded asset onto a persistent asset path
                val decodedAssetPath = kaliumFileSystem.providePersistentAssetPath("${assetId.value}.${assetName.fileExtension()}")
                val decodedAssetSink = kaliumFileSystem.sink(decodedAssetPath)

                // Public assets are stored already decrypted on the backend, hence no decryption is needed
                val assetDataSize = if (encryptionKey != null)
                    decryptFileWithAES256(encryptedAssetDataSource, decodedAssetSink, encryptionKey)
                else
                    kaliumFileSystem.writeData(decodedAssetSink, encryptedAssetDataSource)

                // Delete temp path now that the decoded asset has been persisted correctly
                encryptedAssetDataSource.close()
                kaliumFileSystem.delete(tempFile)

                if (assetDataSize == -1L)
                    Either.Left(EncryptionFailure())

                wrapStorageRequest {
                    assetDao.insertAsset(
                        assetMapper.fromUserAssetToDaoModel(
                            assetId,
                            assetName.fileExtension().fileExtensionToAssetType(),
                            decodedAssetPath,
                            assetDataSize
                        )
                    )
                }
                Either.Right(decodedAssetPath)
            }
        }, {
            Either.Right(it.dataPath.toPath())
        })

    override suspend fun downloadUsersPictureAssets(assetIdList: List<UserAssetId?>): Either<CoreFailure, Unit> {
        assetIdList.filterNotNull().forEach { userAssetId ->
            downloadPublicAsset(idMapper.toQualifiedAssetId(userAssetId.value, userAssetId.domain))
        }
        return Either.Right(Unit)
    }

    override suspend fun deleteAsset(assetId: AssetId, assetToken: String?): Either<CoreFailure, Unit> =
        wrapApiRequest { assetApi.deleteAsset(idMapper.toApiModel(assetId), assetToken) }
            .flatMap { deleteAssetLocally(assetId) }

    override suspend fun deleteAssetLocally(assetId: AssetId): Either<CoreFailure, Unit> =
        wrapStorageRequest { assetDao.deleteAsset(assetId.value) }
}
