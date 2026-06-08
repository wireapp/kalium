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

import com.wire.backup.rootkey.BackupRootKeyExportData
import com.wire.backup.rootkey.BackupRootKeyExportEncryptor
import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalEncodingApi::class)
class ImportBackupRootKeyUseCaseTest {

    @Test
    fun givenValidExportFile_whenImporting_thenBackupRootKeyIsStored() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withExportFile(BACKUP_ROOT_KEY.toExportData(SELF_USER_ID))
            .arrange()

        val result = useCase(arrangement.exportFilePath, PASSWORD)

        val success = assertIs<ImportBackupRootKeyResult.Success>(result)
        assertEquals(BACKUP_ROOT_KEY.id, success.backupRootKey.id)
        assertContentEquals(BACKUP_ROOT_KEY.keyMaterial, success.backupRootKey.keyMaterial)
        assertEquals(BACKUP_ROOT_KEY.id, arrangement.repository.getBackupRootKey()?.id)
    }

    @Test
    fun givenBlankPassword_whenImporting_thenBlankPasswordIsReturned() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withExportFile(BACKUP_ROOT_KEY.toExportData(SELF_USER_ID))
            .arrange()

        val result = useCase(arrangement.exportFilePath, " ")

        assertEquals(ImportBackupRootKeyResult.Failure.BlankPassword, result)
        assertNull(arrangement.repository.getBackupRootKey())
    }

    @Test
    fun givenWrongPassword_whenImporting_thenAuthenticationFailureIsReturned() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withExportFile(BACKUP_ROOT_KEY.toExportData(SELF_USER_ID))
            .arrange()

        val result = useCase(arrangement.exportFilePath, "wrong-password")

        assertEquals(ImportBackupRootKeyResult.Failure.AuthenticationFailure, result)
        assertNull(arrangement.repository.getBackupRootKey())
    }

    @Test
    fun givenMalformedJson_whenImporting_thenInvalidFileIsReturned() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSerializedExportFile("not-json")
            .arrange()

        val result = useCase(arrangement.exportFilePath, PASSWORD)

        assertEquals(ImportBackupRootKeyResult.Failure.InvalidFile, result)
        assertNull(arrangement.repository.getBackupRootKey())
    }

    @Test
    fun givenUnsupportedEnvelope_whenImporting_thenInvalidFileIsReturned() = runTest {
        val unsupportedEnvelope = Arrangement.createSerializedExport(BACKUP_ROOT_KEY.toExportData(SELF_USER_ID))
            .replace("\"format\":\"wire-backup-root-key\"", "\"format\":\"unsupported\"")
        val (arrangement, useCase) = Arrangement()
            .withSerializedExportFile(unsupportedEnvelope)
            .arrange()

        val result = useCase(arrangement.exportFilePath, PASSWORD)

        assertEquals(ImportBackupRootKeyResult.Failure.InvalidFile, result)
        assertNull(arrangement.repository.getBackupRootKey())
    }

    @Test
    fun givenExportBelongsToAnotherUser_whenImporting_thenUserMismatchIsReturnedAndExistingKeyIsKept() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withStoredBackupRootKey(EXISTING_BACKUP_ROOT_KEY)
            .withExportFile(BACKUP_ROOT_KEY.toExportData(UserId("another-user", "wire.com")))
            .arrange()

        val result = useCase(arrangement.exportFilePath, PASSWORD)

        assertEquals(ImportBackupRootKeyResult.Failure.UserMismatch, result)
        assertEquals(EXISTING_BACKUP_ROOT_KEY.id, arrangement.repository.getBackupRootKey()?.id)
    }

    @Test
    fun givenFingerprintDoesNotMatchKeyMaterial_whenImporting_thenFingerprintMismatchIsReturnedAndExistingKeyIsKept() = runTest {
        val mismatchingFingerprintExportData = BACKUP_ROOT_KEY.toExportData(SELF_USER_ID)
            .copy(rootKeyFingerprint = EXISTING_BACKUP_ROOT_KEY.fingerprint())
        val (arrangement, useCase) = Arrangement()
            .withStoredBackupRootKey(EXISTING_BACKUP_ROOT_KEY)
            .withExportFile(mismatchingFingerprintExportData)
            .arrange()

        val result = useCase(arrangement.exportFilePath, PASSWORD)

        assertEquals(ImportBackupRootKeyResult.Failure.FingerprintMismatch, result)
        assertEquals(EXISTING_BACKUP_ROOT_KEY.id, arrangement.repository.getBackupRootKey()?.id)
    }

    @Test
    fun givenExistingBackupRootKey_whenImportSucceeds_thenExistingKeyIsReplaced() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withStoredBackupRootKey(EXISTING_BACKUP_ROOT_KEY)
            .withExportFile(BACKUP_ROOT_KEY.toExportData(SELF_USER_ID))
            .arrange()

        val result = useCase(arrangement.exportFilePath, PASSWORD)

        assertIs<ImportBackupRootKeyResult.Success>(result)
        val storedKey = arrangement.repository.getBackupRootKey()
        assertEquals(BACKUP_ROOT_KEY.id, storedKey?.id)
        assertContentEquals(BACKUP_ROOT_KEY.keyMaterial, storedKey?.keyMaterial)
    }

    private class Arrangement {
        val repository = BackupRootKeyRepositoryImpl(InMemoryMetadataDAO())
        val fileSystem = FakeKaliumFileSystem()
        val exportFilePath: Path = "/Users/me/testApp/cache/imported-backup-root-key.wbrk".toPath()

        suspend fun withStoredBackupRootKey(backupRootKey: BackupRootKey) = apply {
            repository.setBackupRootKey(backupRootKey)
        }

        suspend fun withExportFile(data: BackupRootKeyExportData) = apply {
            withSerializedExportFile(createSerializedExport(data))
        }

        fun withSerializedExportFile(serializedExport: String) = apply {
            fileSystem.sink(exportFilePath).buffer().use { sink ->
                sink.writeUtf8(serializedExport)
            }
        }

        fun arrange(): Pair<Arrangement, ImportBackupRootKeyUseCase> =
            this to ImportBackupRootKeyUseCaseImpl(
                selfUserId = SELF_USER_ID,
                backupRootKeyRepository = repository,
                kaliumFileSystem = fileSystem,
            )

        companion object {
            suspend fun createSerializedExport(data: BackupRootKeyExportData): String =
                BackupRootKeyExportEncryptor.encodeEnvelope(
                    BackupRootKeyExportEncryptor.encrypt(data, PASSWORD)
                )
        }
    }

    private companion object {
        const val PASSWORD = "account-password"
        val SELF_USER_ID = UserId("user-id", "wire.com")
        val BACKUP_ROOT_KEY = BackupRootKey(
            id = "root-key-id",
            keyMaterial = ByteArray(32) { it.toByte() },
            createdAt = Instant.parse("2026-06-06T12:00:00Z"),
            createdByClientId = ClientId("client-id"),
            version = 1,
        )
        val EXISTING_BACKUP_ROOT_KEY = BackupRootKey(
            id = "existing-root-key-id",
            keyMaterial = ByteArray(32) { (it + 1).toByte() },
            createdAt = Instant.parse("2026-06-05T12:00:00Z"),
            createdByClientId = ClientId("existing-client-id"),
            version = 1,
        )
    }
}

@OptIn(ExperimentalEncodingApi::class)
private fun BackupRootKey.toExportData(userId: UserId): BackupRootKeyExportData =
    BackupRootKeyExportData(
        userId = userId.toString(),
        rootKeyId = id,
        rootKeyVersion = version,
        rootKeyFingerprint = fingerprint(),
        createdAt = createdAt.toString(),
        createdByClientId = createdByClientId.value,
        keyMaterial = Base64.encode(keyMaterial),
    )
