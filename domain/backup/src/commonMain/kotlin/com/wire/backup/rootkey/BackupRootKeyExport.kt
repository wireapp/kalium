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
package com.wire.backup.rootkey

import com.wire.backup.encryption.DecryptionResult
import com.wire.backup.encryption.EncryptedStream
import com.wire.backup.encryption.XChaChaPoly1305AuthenticationData
import com.wire.backup.hash.HASH_MEM_LIMIT
import com.wire.backup.hash.HASH_OPS_LIMIT
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.Buffer
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Serializable
public data class BackupRootKeyExportData(
    @SerialName("user_id")
    val userId: String,
    @SerialName("root_key_id")
    val rootKeyId: String,
    @SerialName("root_key_version")
    val rootKeyVersion: Int,
    @SerialName("root_key_fingerprint")
    val rootKeyFingerprint: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("created_by_client_id")
    val createdByClientId: String,
    @SerialName("key_material")
    val keyMaterial: String,
)

@Serializable
public data class EncryptedBackupRootKeyEnvelope(
    @SerialName("format")
    val format: String,
    @SerialName("version")
    val version: Int,
    @SerialName("user_id")
    val userId: String,
    @SerialName("root_key_id")
    val rootKeyId: String,
    @SerialName("root_key_version")
    val rootKeyVersion: Int,
    @SerialName("root_key_fingerprint")
    val rootKeyFingerprint: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("created_by_client_id")
    val createdByClientId: String,
    @SerialName("encryption_algorithm")
    val encryptionAlgorithm: String,
    @SerialName("kdf")
    val kdf: BackupRootKeyExportKdf,
    @SerialName("ciphertext")
    val ciphertext: String,
)

@Serializable
public data class BackupRootKeyExportKdf(
    @SerialName("algorithm")
    val algorithm: String,
    @SerialName("salt")
    val salt: String,
    @SerialName("ops_limit")
    val opsLimit: Long,
    @SerialName("mem_limit")
    val memLimit: Int,
)

public sealed interface BackupRootKeyDecryptResult {
    public data class Success(val data: BackupRootKeyExportData) : BackupRootKeyDecryptResult
    public data object AuthenticationFailure : BackupRootKeyDecryptResult
    public data class UnknownFailure(val message: String) : BackupRootKeyDecryptResult
}

@OptIn(ExperimentalEncodingApi::class, ExperimentalUnsignedTypes::class)
public object BackupRootKeyExportEncryptor {
    public const val FORMAT: String = "wire-backup-root-key"
    public const val VERSION: Int = 1
    public const val ENCRYPTION_ALGORITHM: String = "backup-root-key-password-xchacha20poly1305-argon2id-v1"
    public const val KDF_ALGORITHM: String = "argon2id13"

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    public suspend fun encrypt(data: BackupRootKeyExportData, password: String): EncryptedBackupRootKeyEnvelope {
        val salt = XChaChaPoly1305AuthenticationData.newSalt()
        val authenticatedData = data.toAuthenticatedData(salt)
        val authenticatedDataBytes = json.encodeToString(authenticatedData).encodeToByteArray()
        val plaintext = json.encodeToString(data).encodeToByteArray()
        val output = Buffer()

        EncryptedStream.encrypt(
            source = Buffer().apply { write(plaintext) },
            outputSink = output,
            authenticationData = XChaChaPoly1305AuthenticationData(
                passphrase = password,
                salt = salt,
                additionalData = authenticatedDataBytes.toUByteArray(),
                hashOpsLimit = HASH_OPS_LIMIT,
                hashMemLimit = HASH_MEM_LIMIT,
            )
        )

        return authenticatedData.toEnvelope(
            ciphertext = Base64.encode(output.readByteArray())
        )
    }

    public suspend fun decrypt(envelope: EncryptedBackupRootKeyEnvelope, password: String): BackupRootKeyDecryptResult {
        val authenticatedData = envelope.toAuthenticatedData()
        val output = Buffer()
        val result = EncryptedStream.decrypt(
            source = Buffer().apply { write(Base64.decode(envelope.ciphertext)) },
            outputSink = output,
            authenticationData = XChaChaPoly1305AuthenticationData(
                passphrase = password,
                salt = Base64.decode(envelope.kdf.salt).toUByteArray(),
                additionalData = json.encodeToString(authenticatedData).encodeToByteArray().toUByteArray(),
                hashOpsLimit = envelope.kdf.opsLimit.toULong(),
                hashMemLimit = envelope.kdf.memLimit,
            )
        )

        return when (result) {
            DecryptionResult.Success -> BackupRootKeyDecryptResult.Success(json.decodeFromString(output.readUtf8()))
            DecryptionResult.Failure.AuthenticationFailure -> BackupRootKeyDecryptResult.AuthenticationFailure
            is DecryptionResult.Failure.Unknown -> BackupRootKeyDecryptResult.UnknownFailure(result.message)
        }
    }

    public fun encodeEnvelope(envelope: EncryptedBackupRootKeyEnvelope): String =
        json.encodeToString(envelope)

    public fun decodeEnvelope(serializedEnvelope: String): EncryptedBackupRootKeyEnvelope =
        json.decodeFromString(serializedEnvelope)

    private fun BackupRootKeyExportData.toAuthenticatedData(salt: UByteArray) =
        BackupRootKeyExportAuthenticatedData(
            format = FORMAT,
            version = VERSION,
            userId = userId,
            rootKeyId = rootKeyId,
            rootKeyVersion = rootKeyVersion,
            rootKeyFingerprint = rootKeyFingerprint,
            createdAt = createdAt,
            createdByClientId = createdByClientId,
            encryptionAlgorithm = ENCRYPTION_ALGORITHM,
            kdf = BackupRootKeyExportKdf(
                algorithm = KDF_ALGORITHM,
                salt = Base64.encode(salt.toByteArray()),
                opsLimit = HASH_OPS_LIMIT.toLong(),
                memLimit = HASH_MEM_LIMIT,
            ),
        )

    private fun EncryptedBackupRootKeyEnvelope.toAuthenticatedData() =
        BackupRootKeyExportAuthenticatedData(
            format = format,
            version = version,
            userId = userId,
            rootKeyId = rootKeyId,
            rootKeyVersion = rootKeyVersion,
            rootKeyFingerprint = rootKeyFingerprint,
            createdAt = createdAt,
            createdByClientId = createdByClientId,
            encryptionAlgorithm = encryptionAlgorithm,
            kdf = kdf,
        )

    private fun BackupRootKeyExportAuthenticatedData.toEnvelope(ciphertext: String) =
        EncryptedBackupRootKeyEnvelope(
            format = format,
            version = version,
            userId = userId,
            rootKeyId = rootKeyId,
            rootKeyVersion = rootKeyVersion,
            rootKeyFingerprint = rootKeyFingerprint,
            createdAt = createdAt,
            createdByClientId = createdByClientId,
            encryptionAlgorithm = encryptionAlgorithm,
            kdf = kdf,
            ciphertext = ciphertext,
        )
}

@Serializable
private data class BackupRootKeyExportAuthenticatedData(
    @SerialName("format")
    val format: String,
    @SerialName("version")
    val version: Int,
    @SerialName("user_id")
    val userId: String,
    @SerialName("root_key_id")
    val rootKeyId: String,
    @SerialName("root_key_version")
    val rootKeyVersion: Int,
    @SerialName("root_key_fingerprint")
    val rootKeyFingerprint: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("created_by_client_id")
    val createdByClientId: String,
    @SerialName("encryption_algorithm")
    val encryptionAlgorithm: String,
    @SerialName("kdf")
    val kdf: BackupRootKeyExportKdf,
)
