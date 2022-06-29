package com.wire.kalium.logic.data.asset

import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.decryptFileWithAES256
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
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.asset.AssetApi
import com.wire.kalium.persistence.dao.asset.AssetDAO
import kotlinx.coroutines.flow.firstOrNull
import okio.Path
import okio.Path.Companion.toPath
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
     * Method used to upload and persist to local memory a private asset
     * @param mimeType type of the asset to be uploaded
     * @param encryptedAssetDataPath the path of the encrypted data to be uploaded
     * @return [Either] a [CoreFailure] if anything went wrong, or the [UploadedAssetId] of the asset if successful
     */
    suspend fun uploadAndPersistPrivateAsset(
        mimeType: AssetType,
        encryptedAssetDataPath: Path,
        assetDataSize: Long
    ): Either<CoreFailure, UploadedAssetId>

    /**
     * Method used to download and persist to local memory a public asset
     * @param assetId the asset identifier
     * @return [Either] a [CoreFailure] if anything went wrong, or the [Path] of the decoded asset
     */
    suspend fun downloadPublicAsset(assetId: AssetId): Either<CoreFailure, Path>

    /**
     * Method used to fetch the [Path] of a decoded private asset
     * @param assetId the asset identifier
     * @param assetToken the asset token used to provide an extra layer of asset/user authentication
     * @param encryptionKey the asset encryption key used to decrypt an extra layer of asset/user authentication
     * @return [Either] a [CoreFailure] if anything went wrong, or the [Path] to the decoded asset
     */
    suspend fun fetchPrivateDecodedAsset(assetId: AssetId, assetToken: String?, encryptionKey: AES256Key): Either<CoreFailure, Path>

    /**
     * Method used to download the list of avatar pictures of the current authenticated user
     * @param assetIdList list of the assets' id that wants to be downloaded
     * @return [Either] a [CoreFailure] if anything went wrong, or Unit if operation was successful
     */
    suspend fun downloadUsersPictureAssets(assetIdList: List<UserAssetId?>): Either<CoreFailure, Unit>
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
        return uploadAndPersistAsset(uploadAssetData)
    }

    override suspend fun uploadAndPersistPrivateAsset(
        mimeType: AssetType,
        encryptedAssetDataPath: Path,
        assetDataSize: Long
    ): Either<CoreFailure, UploadedAssetId> {
        val uploadAssetData = UploadAssetData(encryptedAssetDataPath, assetDataSize, mimeType, false, RetentionType.PERSISTENT)
        return uploadAndPersistAsset(uploadAssetData)
    }

    private suspend fun uploadAndPersistAsset(uploadAssetData: UploadAssetData): Either<CoreFailure, UploadedAssetId> =
        assetMapper.toMetadataApiModel(uploadAssetData, kaliumFileSystem).let { metaData ->
            wrapApiRequest {
                val dataSource = kaliumFileSystem.source(uploadAssetData.tempDataPath)

                // we should also consider for avatar images, the compression for preview vs complete picture
                assetApi.uploadAsset(metaData, dataSource, uploadAssetData.dataSize)
            }
        }.flatMap { assetResponse ->
            // After successful upload, we persist the asset to a persistent path
            val persistentAssetDataPath = kaliumFileSystem.providePersistentAssetPath(assetName = assetResponse.key)
            val encryptedAssetSource = kaliumFileSystem.source(uploadAssetData.tempDataPath)

            // After successful upload we write the data now to a persistent path and delete the temporary one
            kaliumFileSystem.writeData(persistentAssetDataPath, encryptedAssetSource)
            kaliumFileSystem.delete(uploadAssetData.tempDataPath)

            // We need to update the asset path with the persistent one
            val persistedAssetData = uploadAssetData.copy(tempDataPath = persistentAssetDataPath)

            assetMapper.fromUploadedAssetToDaoModel(persistedAssetData, assetResponse).let { assetEntity ->
                wrapStorageRequest { assetDao.insertAsset(assetEntity) }
            }.map { assetMapper.fromApiUploadResponseToDomainModel(assetResponse) }
        }

    override suspend fun downloadPublicAsset(assetId: AssetId): Either<CoreFailure, Path> =
        fetchOrDownloadDecodedAsset(assetId = idMapper.toApiModel(assetId), assetToken = null)

    override suspend fun fetchPrivateDecodedAsset(assetId: AssetId, assetToken: String?, encryptionKey: AES256Key): Either<CoreFailure, Path> =
        fetchOrDownloadDecodedAsset(assetId = idMapper.toApiModel(assetId), assetToken = assetToken, encryptionKey = encryptionKey)

    private suspend fun fetchOrDownloadDecodedAsset(
        assetId: NetworkAssetId,
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
                kaliumFileSystem.write(tempFile) {
                    write(assetData)
                }
                val assetDataSource = kaliumFileSystem.source(tempFile)

                // Decrypt and persist decoded asset onto a persistent asset path
                val decodedAssetPath = kaliumFileSystem.providePersistentAssetPath(assetId.value)

                // Public assets are stored already decrypted on the backend, hence no decryption is needed
                val assetDataSize = if (encryptionKey != null)
                    decryptFileWithAES256(assetDataSource, decodedAssetPath, encryptionKey, kaliumFileSystem)
                else
                    kaliumFileSystem.writeData(decodedAssetPath, assetDataSource)

                // Delete temp path now that the decoded asset has been persisted correctly
                kaliumFileSystem.delete(tempFile)

                if (assetDataSize == -1L)
                    Either.Left(EncryptionFailure())
                wrapStorageRequest { assetDao.insertAsset(assetMapper.fromUserAssetToDaoModel(assetId, decodedAssetPath, assetDataSize)) }
                    .map { assetDataSource }
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
}

