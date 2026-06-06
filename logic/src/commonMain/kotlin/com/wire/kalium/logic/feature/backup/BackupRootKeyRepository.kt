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
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal interface BackupRootKeyRepository {
    fun getBackupRootKey(): BackupRootKey?
    fun setBackupRootKey(backupRootKey: BackupRootKey)
}

internal class BackupRootKeyRepositoryImpl(
    private val selfUserId: UserId,
    private val passphraseStorage: PassphraseStorage,
    private val json: Json = Json,
) : BackupRootKeyRepository {

    override fun getBackupRootKey(): BackupRootKey? =
        passphraseStorage.getPassphrase(storageKey)?.let { serializedKey ->
            json.decodeFromString<BackupRootKeyEntity>(serializedKey).toModel()
        }

    override fun setBackupRootKey(backupRootKey: BackupRootKey) {
        passphraseStorage.setPassphrase(storageKey, json.encodeToString(backupRootKey.toEntity()))
    }

    private val storageKey: String
        get() = "${BACKUP_ROOT_KEY_ALIAS_PREFIX}_$selfUserId"

    private companion object {
        const val BACKUP_ROOT_KEY_ALIAS_PREFIX = "backup_root_key_alias"
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
