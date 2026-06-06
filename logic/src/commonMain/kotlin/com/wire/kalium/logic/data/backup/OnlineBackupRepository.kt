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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.base.authenticated.WildCardApi
import io.ktor.http.HttpMethod
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal interface OnlineBackupRepository {
    suspend fun listBackups(): Either<CoreFailure, List<OnlineBackupMetadata>>
    suspend fun registerBackup(metadata: OnlineBackupMetadata): Either<CoreFailure, OnlineBackupMetadata>
}

internal class OnlineBackupDataSource(
    private val wildCardApi: WildCardApi,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
) : OnlineBackupRepository {

    override suspend fun listBackups(): Either<CoreFailure, List<OnlineBackupMetadata>> =
        wrapApiRequest {
            wildCardApi.customRequest(
                httpMethod = HttpMethod.Get,
                requestPath = BACKUP_CATALOG_PATH,
                body = null,
                queryParam = emptyMap(),
                customHeader = emptyMap(),
            )
        }.fold(
            { Either.Left(it) },
            { body ->
                runCatching {
                    json.decodeFromString(OnlineBackupsResponse.serializer(), body).backups.map(OnlineBackupMetadataDTO::toModel)
                }.fold(
                    onSuccess = { Either.Right(it) },
                    onFailure = { Either.Left(CoreFailure.Unknown(it)) },
                )
            }
        )

    override suspend fun registerBackup(metadata: OnlineBackupMetadata): Either<CoreFailure, OnlineBackupMetadata> =
        wrapApiRequest {
            wildCardApi.customRequest(
                httpMethod = HttpMethod.Post,
                requestPath = BACKUP_CATALOG_PATH,
                body = json.encodeToString(metadata.toDTO()),
                queryParam = emptyMap(),
                customHeader = mapOf(CONTENT_TYPE_HEADER to APPLICATION_JSON),
            )
        }.fold(
            { Either.Left(it) },
            { body ->
                runCatching {
                    json.decodeFromString(OnlineBackupMetadataDTO.serializer(), body).toModel()
                }.fold(
                    onSuccess = { Either.Right(it) },
                    onFailure = { Either.Left(CoreFailure.Unknown(it)) },
                )
            }
        )

    private companion object {
        val BACKUP_CATALOG_PATH = listOf("backups")
        const val CONTENT_TYPE_HEADER = "Content-Type"
        const val APPLICATION_JSON = "application/json"
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

@Serializable
private data class OnlineBackupsResponse(
    @SerialName("backups") val backups: List<OnlineBackupMetadataDTO> = emptyList(),
)

@Serializable
private data class OnlineBackupMetadataDTO(
    @SerialName("backup_id") val backupId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("user_domain") val userDomain: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("file_name") val fileName: String,
    @SerialName("last_message_date") val lastMessageDate: String,
    @SerialName("asset_id") val assetId: String,
    @SerialName("asset_domain") val assetDomain: String? = null,
    @SerialName("asset_token") val assetToken: String? = null,
    @SerialName("root_key_id") val rootKeyId: String,
    @SerialName("encryption_algorithm") val encryptionAlgorithm: String,
) {
    fun toModel(): OnlineBackupMetadata = OnlineBackupMetadata(
        backupId = backupId,
        userId = UserId(userId, userDomain),
        clientId = clientId,
        fileName = fileName,
        lastMessageDate = Instant.parse(lastMessageDate),
        assetId = UploadedAssetId(
            key = assetId,
            domain = assetDomain ?: "",
            assetToken = assetToken,
        ),
        rootKeyId = rootKeyId,
        encryptionAlgorithm = encryptionAlgorithm,
    )
}

private fun OnlineBackupMetadata.toDTO(): OnlineBackupMetadataDTO = OnlineBackupMetadataDTO(
    backupId = backupId,
    userId = userId.value,
    userDomain = userId.domain,
    clientId = clientId,
    fileName = fileName,
    lastMessageDate = lastMessageDate.toString(),
    assetId = assetId.key,
    assetDomain = assetId.domain,
    assetToken = assetId.assetToken,
    rootKeyId = rootKeyId,
    encryptionAlgorithm = encryptionAlgorithm,
)
