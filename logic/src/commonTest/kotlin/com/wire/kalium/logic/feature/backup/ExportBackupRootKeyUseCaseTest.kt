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

import com.wire.backup.rootkey.BackupRootKeyDecryptResult
import com.wire.backup.rootkey.BackupRootKeyExportEncryptor
import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

@OptIn(ExperimentalEncodingApi::class)
class ExportBackupRootKeyUseCaseTest {

    @Test
    fun givenNoStoredRootKey_whenExporting_thenNoBackupRootKeyIsReturned() = runTest {
        val (_, useCase) = Arrangement().arrange()

        val result = useCase(PASSWORD)

        assertEquals(ExportBackupRootKeyResult.Failure.NoBackupRootKey, result)
    }

    @Test
    fun givenBlankPassword_whenExporting_thenBlankPasswordIsReturned() = runTest {
        val (_, useCase) = Arrangement()
            .withBackupRootKey(BACKUP_ROOT_KEY)
            .arrange()

        val result = useCase(" ")

        assertEquals(ExportBackupRootKeyResult.Failure.BlankPassword, result)
    }

    @Test
    fun givenStoredRootKey_whenExporting_thenEncryptedEnvelopeIsWritten() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withBackupRootKey(BACKUP_ROOT_KEY)
            .arrange()

        val result = useCase(PASSWORD)

        assertIs<ExportBackupRootKeyResult.Success>(result)
        assertEquals("wire-backup-root-key-root-key-id.wbrk", result.fileName)

        val envelopeJson = arrangement.fileSystem.readByteArray(result.exportFilePath).decodeToString()
        assertFalse(envelopeJson.contains(Base64.encode(BACKUP_ROOT_KEY.keyMaterial)))

        val envelope = BackupRootKeyExportEncryptor.decodeEnvelope(envelopeJson)
        val decryptResult = BackupRootKeyExportEncryptor.decrypt(envelope, PASSWORD)

        assertIs<BackupRootKeyDecryptResult.Success>(decryptResult)
        assertEquals("user-id@wire.com", decryptResult.data.userId)
        assertEquals(BACKUP_ROOT_KEY.id, decryptResult.data.rootKeyId)
        assertContentEquals(BACKUP_ROOT_KEY.keyMaterial, Base64.decode(decryptResult.data.keyMaterial))
    }

    @Test
    fun givenStoredRootKey_whenDecryptingExportWithWrongPassword_thenAuthenticationFails() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withBackupRootKey(BACKUP_ROOT_KEY)
            .arrange()
        val result = assertIs<ExportBackupRootKeyResult.Success>(useCase(PASSWORD))
        val envelopeJson = arrangement.fileSystem.readByteArray(result.exportFilePath).decodeToString()
        val envelope = BackupRootKeyExportEncryptor.decodeEnvelope(envelopeJson)

        val decryptResult = BackupRootKeyExportEncryptor.decrypt(envelope, "wrong-password")

        assertEquals(BackupRootKeyDecryptResult.AuthenticationFailure, decryptResult)
    }

    private class Arrangement {
        val repository = BackupRootKeyRepositoryImpl(InMemoryMetadataDAO())
        val fileSystem = FakeKaliumFileSystem()

        suspend fun withBackupRootKey(backupRootKey: BackupRootKey) = apply {
            repository.setBackupRootKey(backupRootKey)
        }

        fun arrange(): Pair<Arrangement, ExportBackupRootKeyUseCase> =
            this to ExportBackupRootKeyUseCaseImpl(
                selfUserId = UserId("user-id", "wire.com"),
                backupRootKeyRepository = repository,
                kaliumFileSystem = fileSystem,
            )
    }

    private companion object {
        const val PASSWORD = "account-password"
        val BACKUP_ROOT_KEY = BackupRootKey(
            id = "root-key-id",
            keyMaterial = ByteArray(32) { it.toByte() },
            createdAt = Instant.parse("2026-06-06T12:00:00Z"),
            createdByClientId = ClientId("client-id"),
            version = 1,
        )
    }
}
