@file:Suppress("konsist.useCasesShouldNotAccessDaoLayerDirectly")

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
package com.wire.kalium.logic.feature.backup

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.persistence.dao.MetadataDAO
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal interface BackupRootKeyRepository {
    suspend fun getBackupRootKey(): BackupRootKey?
    suspend fun setBackupRootKey(backupRootKey: BackupRootKey)
    suspend fun clearBackupRootKey()
}

internal class BackupRootKeyRepositoryImpl(
    private val metadataDAO: MetadataDAO,
    private val json: Json = Json,
) : BackupRootKeyRepository {

    override suspend fun getBackupRootKey(): BackupRootKey? =
        metadataDAO.valueByKey(STORAGE_KEY)?.let { serializedKey ->
            json.decodeFromString<BackupRootKeyEntity>(serializedKey).toModel()
        }

    override suspend fun setBackupRootKey(backupRootKey: BackupRootKey) {
        metadataDAO.insertValue(
            key = STORAGE_KEY,
            value = json.encodeToString(backupRootKey.toEntity())
        )
    }

    override suspend fun clearBackupRootKey() {
        metadataDAO.deleteValue(STORAGE_KEY)
    }

    private companion object {
        const val STORAGE_KEY = "backup_root_key"
    }
}

internal interface ClearBackupRootKeyUseCase {
    suspend operator fun invoke()
}

internal class ClearBackupRootKeyUseCaseImpl(
    private val backupRootKeyRepository: BackupRootKeyRepository,
) : ClearBackupRootKeyUseCase {
    override suspend fun invoke() {
        backupRootKeyRepository.clearBackupRootKey()
    }
}

@Serializable
private data class BackupRootKeyEntity(
    @SerialName("id")
    val id: String,
    @SerialName("key_material")
    val keyMaterial: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("created_by_client_id")
    val createdByClientId: String,
    @SerialName("version")
    val version: Int,
)

@OptIn(ExperimentalEncodingApi::class)
private fun BackupRootKey.toEntity(): BackupRootKeyEntity =
    BackupRootKeyEntity(
        id = id,
        keyMaterial = Base64.encode(keyMaterial),
        createdAt = createdAt.toString(),
        createdByClientId = createdByClientId.value,
        version = version,
    )

@OptIn(ExperimentalEncodingApi::class)
private fun BackupRootKeyEntity.toModel(): BackupRootKey =
    BackupRootKey(
        id = id,
        keyMaterial = Base64.decode(keyMaterial),
        createdAt = Instant.parse(createdAt),
        createdByClientId = ClientId(createdByClientId),
        version = version,
    )
