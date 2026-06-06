/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.logic.data.backup

import com.wire.kalium.cells.domain.usecase.BackupCellFileUseCase
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.tools.KtxSerializer
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okio.Path
import okio.buffer

internal interface OnlineBackupRepository {
    suspend fun listBackups(): Either<CoreFailure, List<OnlineBackupMetadata>>
    suspend fun registerBackup(metadata: OnlineBackupMetadata): Either<CoreFailure, OnlineBackupMetadata>
    suspend fun downloadBackup(metadata: OnlineBackupMetadata): Either<CoreFailure, Path>
}

internal class OnlineBackupDataSource(
    private val selfUserId: UserId,
    private val backupConversationResolver: BackupConversationProvider,
    private val backupCellFile: BackupCellFileUseCase,
    private val kaliumFileSystem: KaliumFileSystem,
) : OnlineBackupRepository {

    override suspend fun listBackups(): Either<CoreFailure, List<OnlineBackupMetadata>> {
        val conversationId = when (val result = backupConversationResolver.getOrCreateBackupConversation()) {
            is Either.Left -> return result
            is Either.Right -> result.value
        }
        val metadataFiles = when (val result = backupCellFile.listMetadataFiles(conversationId)) {
            is Either.Left -> return result
            is Either.Right -> result.value
        }
        return Either.Right(
            metadataFiles.mapNotNull { metadataFile ->
                readMetadataFile(metadataFile.path)
            }.filter { it.userId == selfUserId }
        )
    }

    override suspend fun registerBackup(metadata: OnlineBackupMetadata): Either<CoreFailure, OnlineBackupMetadata> =
        backupConversationResolver.getOrCreateBackupConversation().flatMap { conversationId ->
            val metadataFileName = metadata.metadataFileName()
            val metadataPath = kaliumFileSystem.tempFilePath(metadataFileName)
            try {
                kaliumFileSystem.sink(metadataPath).buffer().use {
                    it.writeUtf8(KtxSerializer.json.encodeToString(metadata.toDto()))
                }
                backupCellFile.upload(conversationId, metadataPath, metadataFileName).map {
                    metadata
                }
            } finally {
                kaliumFileSystem.delete(metadataPath, mustExist = false)
            }
        }

    override suspend fun downloadBackup(metadata: OnlineBackupMetadata): Either<CoreFailure, Path> {
        val remotePath = metadata.assetId.assetToken
            ?: return Either.Left(StorageFailure.DataNotFound)
        val outputPath = kaliumFileSystem.tempFilePath(metadata.fileName)
        return when (val result = backupCellFile.download(remotePath, outputPath)) {
            is Either.Left -> {
                kaliumFileSystem.delete(outputPath, mustExist = false)
                result
            }
            is Either.Right -> Either.Right(outputPath)
        }
    }

    private suspend fun readMetadataFile(remotePath: String): OnlineBackupMetadata? {
        val outputPath = kaliumFileSystem.tempFilePath("${remotePath.substringAfterLast('/')}-download")
        return try {
            when (backupCellFile.download(remotePath, outputPath)) {
                is Either.Left -> null
                is Either.Right -> kaliumFileSystem.source(outputPath).buffer().use {
                    KtxSerializer.json.decodeFromString<OnlineBackupMetadataDTO>(it.readUtf8()).toModel()
                }
            }
        } catch (_: Exception) {
            null
        } finally {
            kaliumFileSystem.delete(outputPath, mustExist = false)
        }
    }
}

public data class OnlineBackupMetadata(
    public val backupId: String,
    public val userId: UserId,
    public val clientId: String,
    public val fileName: String,
    public val lastMessageDate: Instant,
    public val assetId: UploadedAssetId,
    public val rootKeyId: String,
    public val encryptionAlgorithm: String,
)

private fun OnlineBackupMetadata.metadataFileName(): String = "$fileName.metadata.json"

private fun OnlineBackupMetadata.toDto(): OnlineBackupMetadataDTO =
    OnlineBackupMetadataDTO(
        backupId = backupId,
        userId = userId.value,
        userDomain = userId.domain,
        clientId = clientId,
        fileName = fileName,
        lastMessageDate = lastMessageDate.toString(),
        assetKey = assetId.key,
        assetDomain = assetId.domain,
        assetToken = assetId.assetToken,
        rootKeyId = rootKeyId,
        encryptionAlgorithm = encryptionAlgorithm,
    )

private fun OnlineBackupMetadataDTO.toModel(): OnlineBackupMetadata =
    OnlineBackupMetadata(
        backupId = backupId,
        userId = UserId(userId, userDomain),
        clientId = clientId,
        fileName = fileName,
        lastMessageDate = Instant.parse(lastMessageDate),
        assetId = UploadedAssetId(
            key = assetKey,
            domain = assetDomain,
            assetToken = assetToken,
        ),
        rootKeyId = rootKeyId,
        encryptionAlgorithm = encryptionAlgorithm,
    )

@Serializable
private data class OnlineBackupMetadataDTO(
    @SerialName("backup_id")
    val backupId: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("user_domain")
    val userDomain: String,
    @SerialName("client_id")
    val clientId: String,
    @SerialName("file_name")
    val fileName: String,
    @SerialName("last_message_date")
    val lastMessageDate: String,
    @SerialName("asset_key")
    val assetKey: String,
    @SerialName("asset_domain")
    val assetDomain: String,
    @SerialName("asset_token")
    val assetToken: String?,
    @SerialName("root_key_id")
    val rootKeyId: String,
    @SerialName("encryption_algorithm")
    val encryptionAlgorithm: String,
)
