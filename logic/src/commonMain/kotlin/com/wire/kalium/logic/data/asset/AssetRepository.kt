package com.wire.kalium.logic.data.asset

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.asset.AssetApi
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.persistence.dao.asset.AssetDAO
import kotlinx.coroutines.flow.firstOrNull
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

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
     * @param assetKey the asset identifier
     * @return [Either] a [CoreFailure] if anything went wrong, or the path to the decoded asset
     */
    suspend fun downloadPublicAsset(assetKey: String): Either<CoreFailure, Path>

    /**
     * Method used to download and persist to local memory a private asset
     * @param assetKey the asset identifier
     * @param assetKeyDomain the domain of the asset identifier allowing federated asset handling
     * @param assetToken the asset token used to provide an extra layer of asset/user authentication
     * @return [Either] a [CoreFailure] if anything went wrong, or the path to the encoded asset
     */
    suspend fun downloadPrivateAsset(assetKey: String, assetKeyDomain: String?, assetToken: String?): Either<CoreFailure, Path>

    /**
     * Method used to download the list of avatar pictures of the current logged in user
     * @param assetIdList list of the assets' id that wants to be downloaded
     * @return [Either] a [CoreFailure] if anything went wrong, or Unit if operation was successful
     */
    suspend fun downloadUsersPictureAssets(assetIdList: List<UserAssetId?>): Either<CoreFailure, Unit>
}

internal class AssetDataSource(
    private val assetApi: AssetApi,
    private val assetDao: AssetDAO,
    private val assetMapper: AssetMapper = MapperProvider.assetMapper(),
    private val kaliumFileSystem: KaliumFileSystem
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
                val tempOutputPath = kaliumFileSystem.tempFilePath("temp_output")
                val outputSink = kaliumFileSystem.sink(tempOutputPath)
                val dataSource = kaliumFileSystem.source(uploadAssetData.dataPath)

                // we should also consider for avatar images, the compression for preview vs complete picture
                assetApi.uploadAsset(metaData, outputSink, dataSource, uploadAssetData.dataSize)
            }
        }.flatMap { assetResponse ->
            assetMapper.fromUploadedAssetToDaoModel(uploadAssetData, assetResponse).let { assetEntity ->
                wrapStorageRequest { assetDao.insertAsset(assetEntity) }
            }.map { assetMapper.fromApiUploadResponseToDomainModel(assetResponse) }
        }

    override suspend fun downloadPublicAsset(assetKey: String): Either<CoreFailure, Path> =
        downloadAsset(assetKey = assetKey, assetKeyDomain = null, assetToken = null)

    override suspend fun downloadPrivateAsset(
        assetKey: String,
        assetKeyDomain: String?,
        assetToken: String?
    ): Either<CoreFailure, Path> = downloadAsset(assetKey, assetKeyDomain, assetToken)

    private suspend fun downloadAsset(
        assetKey: String,
        assetKeyDomain: String?,
        assetToken: String?
    ): Either<CoreFailure, Path> {
        return if (assetKeyDomain == null) {
            Either.Left(
                NetworkFailure.ServerMiscommunication(
                    KaliumException.GenericError(
                        IllegalStateException("The asset Key Domain can't be null")
                    )
                )
            )
        } else
            wrapStorageRequest { assetDao.getAssetByKey(assetKey).firstOrNull() }.fold({
                wrapApiRequest {
                    assetApi.downloadAsset(assetKey, assetKeyDomain, assetToken?.ifEmpty { null })
                }.flatMap { assetDataSource ->
                    val assetDataPath = assetKey.toPath()
                    val mustCreate = !kaliumFileSystem.exists(assetDataPath)
                    var assetDataSize = 0L

                    kaliumFileSystem.write(assetKey.toPath(), mustCreate) {
                        assetDataSize = writeAll(assetDataSource)
                    }

                    wrapStorageRequest {
                        assetDao.insertAsset(
                            assetMapper.fromUserAssetToDaoModel(
                                assetKey,
                                assetKeyDomain,
                                assetDataPath,
                                assetDataSize
                            )
                        )
                    }
                        .map { assetDataSource }
                    Either.Right(assetKey.toPath())
                }
            }, { Either.Right(assetKey.toPath()) })
    }

    override suspend fun downloadUsersPictureAssets(assetIdList: List<UserAssetId?>): Either<CoreFailure, Unit> {
        assetIdList.filterNotNull().forEach {
            downloadPublicAsset(it)
        }
        return Either.Right(Unit)
    }
}

