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

@file:OptIn(com.wire.kalium.util.InternalKaliumApi::class)

package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.util.InternalKaliumApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Required
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toPath
import platform.Foundation.NSUUID
import platform.posix.ENOENT
import platform.posix.errno
import platform.posix.unlink

/** Login-prepared configuration; these checks do not make native crypto opening existing-only. */
@Serializable
@InternalKaliumApi
public data class PreparedAccount(
    @SerialName("user_id") val userId: UserId,
    @SerialName("client_id") val clientId: String,
    @SerialName("server_config") val serverConfig: ServerConfig,
    @SerialName("mls_cipher_suite") val mlsCipherSuite: CipherSuite?,
    @SerialName("nomad_service_url") val nomadServiceUrl: String?,
    @Required @SerialName("format_version") val formatVersion: Int = PREPARED_ACCOUNT_FORMAT,
    @Required @SerialName("user_schema_version") val userSchemaVersion: Long = UserDatabase.Schema.version,
    @Required @SerialName("core_crypto_version") val coreCryptoVersion: String = PREPARED_CORE_CRYPTO_VERSION,
) {
    internal fun isCompatible(accountId: UserId): Boolean = userId == accountId &&
        clientId.isNotBlank() && formatVersion == PREPARED_ACCOUNT_FORMAT &&
        userSchemaVersion == UserDatabase.Schema.version && coreCryptoVersion == PREPARED_CORE_CRYPTO_VERSION
}

/**
 * Readiness can be revoked without opening user SQLite, even when its schema is incompatible.
 * All operations require ownership. Publishing follows completed app preparation; NSE only reads.
 */
@InternalKaliumApi
public class ApplePreparedAccountStore(private val rootPath: String, private val userId: UserId) {
    private val rootAccountPath = appleAccountPath(rootPath, userId)
    private val path = "$rootAccountPath/$FILE_NAME".toPath()

    public fun revoke(lease: AccountRuntimeLease) {
        lease.requireHeld(rootPath, userId)
        if (unlink(path.toString()) != 0 && errno != ENOENT) {
            throw IOException("Cannot revoke prepared account readiness")
        }
    }

    @Suppress("ReturnCount")
    public fun read(lease: AccountRuntimeLease): PreparedAccount? {
        lease.requireHeld(rootPath, userId)
        val metadata = FileSystem.SYSTEM.metadataOrNull(path) ?: return null
        if (!metadata.isRegularFile || (metadata.size ?: MAX_READINESS_BYTES) > MAX_READINESS_BYTES) return null
        val encoded = FileSystem.SYSTEM.read(path) { readUtf8() }
        val prepared = try {
            json.decodeFromString<PreparedAccount>(encoded)
        } catch (_: SerializationException) {
            return null
        }
        return prepared.takeIf { it.isCompatible(lease.accountId) }
    }

    public fun publish(lease: AccountRuntimeLease, prepared: PreparedAccount) {
        lease.requireHeld(rootPath, userId)
        require(prepared.isCompatible(lease.accountId)) { "Prepared account identity or compatibility mismatch" }
        val encoded = json.encodeToString(prepared)
        require(encoded.encodeToByteArray().size <= MAX_READINESS_BYTES) { "Prepared account configuration is too large" }
        val temporary = "$rootAccountPath/.$FILE_NAME.${NSUUID.UUID().UUIDString}.tmp".toPath()
        try {
            // Okio checks writes and closes before the final name can become visible.
            FileSystem.SYSTEM.write(temporary, mustCreate = true) { writeUtf8(encoded) }
            FileSystem.SYSTEM.atomicMove(temporary, path)
        } finally {
            // Temporary files are never considered readiness. Preserve the original failure.
            unlink(temporary.toString())
        }
    }

    private companion object {
        const val FILE_NAME = "notification-extension-ready.json"
        const val MAX_READINESS_BYTES = 32_768L
        val json = Json { encodeDefaults = true }
    }
}

@InternalKaliumApi
public fun appleAccountPath(rootPath: String, userId: UserId): String = "$rootPath/${userId.domain}/${userId.value}"

private const val PREPARED_ACCOUNT_FORMAT = 1
