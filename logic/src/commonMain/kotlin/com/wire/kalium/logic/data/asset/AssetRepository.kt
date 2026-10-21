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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.EncryptionFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.cryptography.utils.calcFileSHA256
import com.wire.kalium.cryptography.utils.decryptFileWithAES256
import com.wire.kalium.cryptography.utils.encryptFileWithAES256
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.util.fileExtension
import com.wire.kalium.network.api.base.authenticated.asset.AssetApi
import com.wire.kalium.network.api.model.ConversationId
import com.wire.kalium.persistence.dao.asset.AssetDAO
import com.wire.kalium.util.getExtensionFromMimeType
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.firstOrNull
import okio.Buffer
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.use
import kotlin.uuid.Uuid

internal interface AssetRepository {
    /**
     * Method used to upload and persist to local memory a public asset
     * @param mimeType type of the asset to be uploaded
     * @param assetDataPath the path of the unencrypted data to be uploaded
     * @param assetDataSize the size of the unencrypted data to be uploaded
     * @param conversationId optional conversation ID for audit logging
     * @param filename optional filename for audit logging
     * @param filetype optional file type for audit logging
     * @return [Either] a [CoreFailure] if anything went wrong, or the [UploadedAssetId] of the asset if successful
     */

    @Suppress("LongParameterList")
    suspend fun uploadAndPersistPublicAsset(
        mimeType: String,
        assetDataPath: Path,
        assetDataSize: Long,
        conversationId: ConversationId? = null,
        filename: String? = null,
        filetype: String? = null,
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
    @Suppress("LongParameterList")
    suspend fun uploadAndPersistPrivateAsset(
        mimeType: String,
        assetDataPath: Path,
        otrKey: AES256Key,
        extension: String?,
        conversationId: ConversationId? = null,
        filename: String? = null,
        filetype: String? = null,
        retainSource: Boolean = false,
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
     * @return [Either] a [CoreFailure] if anything went wrong, or the [FetchedAssetData] which consists of [Path] to the decoded asset
     * and a [Boolean] indicating whether the asset has just been downloaded (true) or was already present locally (false)
     */
    suspend fun downloadPublicAsset(assetId: String, assetDomain: String?): Either<CoreFailure, FetchedAssetData>

    /**
     * Method used to fetch the [Path] of a decoded private asset
     * @param assetId the asset identifier
     * @param assetName the name of the original asset
     * @param assetToken the asset token used to provide an extra layer of asset/user authentication
     * @param encryptionKey the asset encryption key used to decrypt an extra layer of asset/user authentication
     * @param downloadIfNeeded flag determining whether it should make a request do download an asset if it's not available locally
     * @return [Either] a [CoreFailure] if anything went wrong, or the [FetchedAssetData] which consists of [Path] to the decoded asset
     * and a [Boolean] indicating whether the asset has just been downloaded (true) or was already present locally (false)
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
    ): Either<CoreFailure, FetchedAssetData>

    /**
     * Method used to delete asset locally and externally
     */
    suspend fun deleteAsset(assetId: String, assetDomain: String?, assetToken: String?): Either<CoreFailure, Unit>

    /**
     * Method used to delete asset only locally
     */
    suspend fun deleteAssetLocally(assetId: String): Either<CoreFailure, Unit>

    /** Delete only the cached asset matching the normalized remote domain. */
    suspend fun deleteAssetLocally(assetId: String, assetDomain: String?): Either<CoreFailure, Unit>
    suspend fun fetchDecodedAsset(assetId: String): Either<CoreFailure, Path>

    /** Fetches an optional preview with independent transfer, decoded-file and cached-file size limits. */
    suspend fun fetchPreviewAsset(preview: AssetContent.Preview): Either<CoreFailure, FetchedAssetData>
}

@Suppress("TooManyFunctions")
internal class AssetDataSource(
    private val assetApi: AssetApi,
    private val assetDao: AssetDAO,
    private val selfUserId: UserId,
    private val assetMapper: AssetMapper = MapperProvider.assetMapper(),
    private val assetAuditLog: Lazy<AssetAuditFeatureHandler>,
    private val kaliumFileSystem: KaliumFileSystem
) : AssetRepository {

    override suspend fun uploadAndPersistPublicAsset(
        mimeType: String,
        assetDataPath: Path,
        assetDataSize: Long,
        conversationId: ConversationId?,
        filename: String?,
        filetype: String?,
    ): Either<CoreFailure, UploadedAssetId> {
        val uploadAssetData = buildPublicAssetData(
            path = assetDataPath,
            size = assetDataSize,
            mimeType = mimeType,
            conversationId = conversationId,
            filename = filename,
            filetype = filetype
        )
        return uploadAndPersistAsset(uploadAssetData, assetDataPath, null)
    }

    override suspend fun uploadAndPersistPrivateAsset(
        mimeType: String,
        assetDataPath: Path,
        otrKey: AES256Key,
        extension: String?,
        conversationId: ConversationId?,
        filename: String?,
        filetype: String?,
        retainSource: Boolean,
    ): Either<CoreFailure, Pair<UploadedAssetId, SHA256Key>> =
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

            if (encryptionSucceeded) {
                val uploadAssetData = buildAssetData(tempEncryptedDataPath, encryptedDataSize, mimeType, conversationId, filename, filetype)
                uploadAndPersistAsset(uploadAssetData, assetDataPath, extension, retainSource).map { it to SHA256Key(sha256) }
            } else {
                kaliumLogger.e("Something went wrong when encrypting the Asset Message")
                Either.Left(EncryptionFailure.GenericEncryptionError)
            }
        } catch (e: IOException) {
            kaliumLogger.e("Something went wrong when uploading the Asset Message. $e")
            Either.Left(CoreFailure.Unknown(e))
        }

    @Suppress("LongParameterList")
    private suspend fun buildAssetData(
        path: Path,
        size: Long,
        mimeType: String,
        conversationId: ConversationId?,
        filename: String?,
        filetype: String?
    ): UploadAssetData = UploadAssetData(path, size, mimeType, false, RetentionType.PERSISTENT)
        .run {
            if (assetAuditLog.value.isAssetAuditLogEnabled()) {
                // Initialize asset audit log fields if the feature is enabled
                copy(
                    conversationId = conversationId,
                    filename = filename,
                    filetype = filetype,
                )
            } else {
                this
            }
        }

    @Suppress("LongParameterList")
    private suspend fun buildPublicAssetData(
        path: Path,
        size: Long,
        mimeType: String,
        // for public assets we set conversationId to "00000000-0000-0000-0000-000000000000" for more info check confluence
        conversationId: ConversationId?,
        filename: String?,
        filetype: String?
    ): UploadAssetData = UploadAssetData(path, size, mimeType, true, RetentionType.ETERNAL)
        .run {
            if (assetAuditLog.value.isAssetAuditLogEnabled()) {
                // Initialize asset audit log fields if the feature is enabled
                copy(
                    conversationId = conversationId ?: publicAssetConversationId(),
                    filename = filename,
                    filetype = filetype,
                )
            } else {
                this
            }
        }

    private fun publicAssetConversationId(): ConversationId =
        ConversationId(PUBLIC_ASSET_CONVERSATION_ID, selfUserId.domain)

    private suspend fun uploadAndPersistAsset(
        uploadAssetData: UploadAssetData,
        decodedDataPath: Path,
        extension: String?,
        retainSource: Boolean = false,
    ): Either<CoreFailure, UploadedAssetId> =
        assetMapper.toMetadataApiModel(uploadAssetData, kaliumFileSystem).let { metaData ->
            wrapApiRequest {
                // Authentication retries must reopen the file; the transport closes each attempt's source.
                assetApi.uploadAsset(
                    metaData,
                    { kaliumFileSystem.source(uploadAssetData.tempEncryptedDataPath) },
                    uploadAssetData.dataSize
                )
            }
        }
            .flatMap { assetResponse ->
                // After successful upload, we persist the asset to a persistent path
                persistAsset(assetResponse.key, assetResponse.domain, decodedDataPath, uploadAssetData.dataSize, extension, retainSource)
                    .also { kaliumFileSystem.delete(uploadAssetData.tempEncryptedDataPath) }
                    .map { assetMapper.fromApiUploadResponseToDomainModel(assetResponse) }
            }

    override suspend fun persistAsset(
        assetId: String,
        assetDomain: String?,
        decodedDataPath: Path,
        assetDataSize: Long,
        extension: String?
    ): Either<CoreFailure, Path> = persistAsset(assetId, assetDomain, decodedDataPath, assetDataSize, extension, retainSource = false)

    @Suppress("LongParameterList")
    private suspend fun persistAsset(
        assetId: String,
        assetDomain: String?,
        decodedDataPath: Path,
        assetDataSize: Long,
        extension: String?,
        retainSource: Boolean,
    ): Either<CoreFailure, Path> {
        return wrapStorageRequest {
            val persistentAssetDataPath = kaliumFileSystem.providePersistentAssetPath(assetName = buildFileName(assetId, extension))
            kaliumFileSystem.copy(decodedDataPath, persistentAssetDataPath)
            // Keep explicitly retained and managed sources until the message update commits.
            if (!retainSource && !assetDao.isFileReferenced(decodedDataPath.toString())) kaliumFileSystem.delete(decodedDataPath)
            val decodedAssetEntity = assetMapper.fromUserAssetToDaoModel(assetId, assetDomain, persistentAssetDataPath, assetDataSize)
            assetDao.insertAsset(decodedAssetEntity)
            return@wrapStorageRequest persistentAssetDataPath
        }
    }

    override suspend fun downloadPublicAsset(assetId: String, assetDomain: String?): Either<CoreFailure, FetchedAssetData> =
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
    ): Either<CoreFailure, FetchedAssetData> =
        if (!downloadIfNeeded) fetchDecodedAsset(assetId = assetId).map { FetchedAssetData(it, false) }
        else fetchOrDownloadDecodedAsset(
            assetId = assetId,
            assetDomain = assetDomain,
            assetName = assetName,
            mimeType = mimeType,
            assetToken = assetToken,
            encryptionKey = encryptionKey,
            assetSHA256 = assetSHA256Key
        )

    override suspend fun fetchPreviewAsset(preview: AssetContent.Preview): Either<CoreFailure, FetchedAssetData> {
        if (preview.sizeInBytes !in 1..AssetContent.Preview.MAX_SIZE_IN_BYTES) {
            return Either.Left(StorageFailure.Generic(IOException("Asset preview exceeds size limit")))
        }
        val remote = preview.remoteData
        return fetchDecodedAsset(remote.assetId).fold(
            {
                // Pending previews only have a managed local file and must never trigger a remote request.
                if (remote.sha256.isEmpty()) Either.Left(it)
                else downloadDecodedAsset(
                    assetId = remote.assetId,
                    assetDomain = remote.assetDomain,
                    assetName = "preview.jpg",
                    assetToken = remote.assetToken,
                    mimeType = preview.mimeType,
                    encryptionKey = AES256Key(remote.otrKey),
                    assetSHA256 = SHA256Key(remote.sha256),
                    maxDecodedSize = AssetContent.Preview.MAX_SIZE_IN_BYTES,
                )
            },
            { path -> cachedPreviewData(path) },
        )
    }

    private fun cachedPreviewData(path: Path): Either<CoreFailure, FetchedAssetData> = wrapStorageRequest {
        val size = kaliumFileSystem.size(path)
        if (size == null || size !in 1..AssetContent.Preview.MAX_SIZE_IN_BYTES) {
            // The same remote ID may also be referenced as an original attachment.
            throw IOException("Cached asset preview exceeds size limit")
        }
        FetchedAssetData(path, false)
    }

    override suspend fun fetchDecodedAsset(assetId: String): Either<CoreFailure, Path> =
        wrapStorageRequest { assetDao.getAssetByKey(assetId).firstOrNull() }
            .flatMap { asset ->
                wrapStorageRequest {
                    val path = asset.dataPath.toPath()
                    if (kaliumFileSystem.exists(path)) path else {
                        assetDao.deleteAsset(assetId)
                        null
                    }
                }
            }

    @Suppress("LongParameterList")
    private suspend fun fetchOrDownloadDecodedAsset(
        assetId: String,
        assetDomain: String?,
        assetName: String,
        assetToken: String?,
        mimeType: String?,
        encryptionKey: AES256Key? = null,
        assetSHA256: SHA256Key? = null
    ): Either<CoreFailure, FetchedAssetData> = fetchDecodedAsset(assetId)
        .map {
            FetchedAssetData(it, false) // Asset found locally, no need to download, return the local path with 'false' flag
        }
        .flatMapLeft {
            downloadDecodedAsset(assetId, assetDomain, assetName, assetToken, mimeType, encryptionKey, assetSHA256)
        }

    @Suppress("LongParameterList")
    private suspend fun downloadDecodedAsset(
        assetId: String,
        assetDomain: String?,
        assetName: String,
        assetToken: String?,
        mimeType: String?,
        encryptionKey: AES256Key?,
        assetSHA256: SHA256Key?,
        maxDecodedSize: Long? = null,
    ): Either<CoreFailure, FetchedAssetData> {
        val tempFile = kaliumFileSystem.tempFilePath("temp_${assetId}_${Uuid.random()}")
        var decodedAssetPath: Path? = null
        var persisted = false
        return try {
            val result = downloadEncryptedAsset(assetId, assetDomain, assetToken, tempFile, maxDecodedSize).flatMap {
                val path = provideDecodedAssetPath(assetId, assetName, mimeType, maxDecodedSize)
                decodedAssetPath = path
                decodeAndPersistAsset(tempFile, path, assetId, assetDomain, encryptionKey, assetSHA256, maxDecodedSize)
            }
            persisted = result.getOrNull()?.path == decodedAssetPath
            result
        } catch (e: IOException) {
            kaliumLogger.e("Something went wrong when handling the Asset paths on the file system", e)
            Either.Left(StorageFailure.DataNotFound)
        } finally {
            // Covers failed downloads, decryption/size failures and coroutine cancellation.
            cleanupDownloadedFiles(tempFile, decodedAssetPath, persisted)
        }
    }

    private suspend fun downloadEncryptedAsset(
        assetId: String,
        assetDomain: String?,
        assetToken: String?,
        tempFile: Path,
        maxDecodedSize: Long?,
    ): Either<CoreFailure, Unit> {
        // Asset encryption uses AES-CBC: a 16-byte IV plus one block of PKCS7 padding at most.
        val maxEncryptedSize = maxDecodedSize?.let { (it / AES_BLOCK_SIZE + 1) * AES_BLOCK_SIZE + AES_BLOCK_SIZE }
        return kaliumFileSystem.sink(tempFile).withSizeLimit(maxEncryptedSize).use { sink ->
            wrapApiRequest {
                // Backend sends asset messages with empty asset tokens.
                assetApi.downloadAsset(assetId, assetDomain, assetToken?.ifEmpty { null }, sink)
            }
        }
    }

    private fun provideDecodedAssetPath(assetId: String, assetName: String, mimeType: String?, maxDecodedSize: Long?): Path {
        val cacheName = if (maxDecodedSize == null) assetId else "${assetId}_${Uuid.random()}"
        return kaliumFileSystem.providePersistentAssetPath(
            buildFileName(cacheName, assetName.fileExtension() ?: getExtensionFromMimeType(mimeType))
        )
    }

    @Suppress("LongParameterList")
    private suspend fun decodeAndPersistAsset(
        tempFile: Path,
        path: Path,
        assetId: String,
        assetDomain: String?,
        encryptionKey: AES256Key?,
        assetSHA256: SHA256Key?,
        maxDecodedSize: Long?,
    ): Either<CoreFailure, FetchedAssetData> {
        val encryptionKeys = when {
            encryptionKey == null -> null
            assetSHA256 == null -> return Either.Left(EncryptionFailure.WrongAssetHash)
            else -> encryptionKey to assetSHA256
        }
        val decodedSink = kaliumFileSystem.sink(path).withSizeLimit(maxDecodedSize)
        val (hashError, assetDataSize) = decodedSink.use { decodeAssetIfNeeded(tempFile, encryptionKeys, it) }
        currentCoroutineContext().ensureActive()
        return when {
            hashError != null -> Either.Left(hashError)
            assetDataSize <= 0L -> Either.Left(EncryptionFailure.GenericDecryptionError)
            decodedSizeExceedsLimit(assetDataSize, decodedSink, maxDecodedSize) ->
                Either.Left(StorageFailure.Generic(IOException("Decoded asset preview exceeds size limit")))
            else -> saveAssetInDB(assetId, assetDomain, path, assetDataSize).flatMap {
                if (maxDecodedSize == null) Either.Right(FetchedAssetData(path, true))
                else fetchDecodedAsset(assetId).flatMap { cachedPreviewData(it) }.map {
                    // INSERT OR IGNORE may retain a concurrent download. Return its managed path;
                    // the outer cleanup removes our unused candidate when the paths differ.
                    it.copy(justDownloaded = it.path == path)
                }
            }
        }
    }

    private suspend fun cleanupDownloadedFiles(tempFile: Path, decodedAssetPath: Path?, persisted: Boolean) = withContext(NonCancellable) {
        try {
            kaliumFileSystem.delete(tempFile, mustExist = false)
        } finally {
            // Cancellation can arrive after the DAO commits but before returning its result.
            // The database is authoritative even when the caller did not observe success.
            if (!persisted && decodedAssetPath != null && !assetDao.isFileReferenced(decodedAssetPath.toString())) {
                kaliumFileSystem.delete(decodedAssetPath, mustExist = false)
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
            .flatMap { deleteAssetLocally(assetId, assetDomain) }

    override suspend fun deleteAssetLocally(assetId: String, assetDomain: String?): Either<CoreFailure, Unit> =
        wrapStorageRequest {
            val asset = assetDao.getAssetByKey(assetId).firstOrNull()
            if (asset != null && asset.domain.orEmpty() == assetDomain.orEmpty()) {
                kaliumFileSystem.delete(asset.dataPath.toPath(), mustExist = false)
                assetDao.deleteAsset(assetId)
            }
        }

    override suspend fun deleteAssetLocally(assetId: String): Either<CoreFailure, Unit> =
        wrapStorageRequest {
            assetDao.getAssetByKey(assetId).firstOrNull()?.let { asset ->
                kaliumFileSystem.delete(asset.dataPath.toPath(), mustExist = false)
            }
            // Keep the cache record if the file could not be removed.
            assetDao.deleteAsset(assetId)
        }
}

private fun buildFileName(name: String, extension: String?): String =
    extension?.let { "$name.$extension" } ?: name

private const val PUBLIC_ASSET_CONVERSATION_ID = "00000000-0000-0000-0000-000000000000"

internal data class FetchedAssetData(val path: Path, val justDownloaded: Boolean)

/** A throwing sink stops the streaming HTTP reader before an oversized response reaches disk. */
private class BoundedAssetSink(private val delegate: Sink, private val maxBytes: Long) : Sink by delegate {
    private var bytesWritten = 0L
    var limitExceeded: Boolean = false
        private set

    override fun write(source: Buffer, byteCount: Long) {
        if (byteCount > maxBytes - bytesWritten) {
            limitExceeded = true
            throw IOException("Asset preview exceeds size limit")
        }
        delegate.write(source, byteCount)
        bytesWritten += byteCount
    }
}

private const val AES_BLOCK_SIZE = 16L

private fun Sink.withSizeLimit(maxBytes: Long?): Sink = if (maxBytes == null) this else BoundedAssetSink(this, maxBytes)

private fun decodedSizeExceedsLimit(size: Long, sink: Sink, maxBytes: Long?): Boolean {
    if (maxBytes == null) return false
    return size > maxBytes || (sink as BoundedAssetSink).limitExceeded
}
