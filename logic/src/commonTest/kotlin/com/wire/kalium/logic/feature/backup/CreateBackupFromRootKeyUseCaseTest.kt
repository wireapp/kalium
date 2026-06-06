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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class CreateBackupFromRootKeyUseCaseTest {

    @Test
    fun givenNoStoredRootKey_whenCreatingBackup_thenRootKeyIsGeneratedPersistedAndBackupIsEncrypted() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withGeneratedKeyId("generated-key")
            .arrange()

        val result = useCase {}

        assertIs<CreateBackupFromRootKeyResult.Success>(result)
        assertEquals("backup-id-1", result.backupId)
        assertEquals("generated-key", result.rootKeyId)
        assertEquals(HkdfBackupEncryptionKeyDeriver.ENCRYPTION_ALGORITHM, result.encryptionAlgorithm)
        assertEquals("generated-key", arrangement.repository.getBackupRootKey()?.id)
        assertNotNull(arrangement.createMPBackup.receivedPassphrase)
        assertFalse(arrangement.createMPBackup.receivedPassphrase!!.isBlank())
    }

    @Test
    fun givenStoredRootKey_whenCreatingBackup_thenExistingRootKeyIsReused() = runTest {
        val existingRootKey = backupRootKey("existing-key", ByteArray(32) { 7 })
        val (arrangement, useCase) = Arrangement()
            .withStoredRootKey(existingRootKey)
            .withGeneratedKeyId("new-key")
            .arrange()

        val result = useCase {}

        assertIs<CreateBackupFromRootKeyResult.Success>(result)
        assertEquals("existing-key", result.rootKeyId)
        assertEquals("existing-key", arrangement.repository.getBackupRootKey()?.id)
    }

    @Test
    fun givenStoredRootKey_whenCreatingBackup_thenDerivedPassphraseIsSentToMPBackupCreation() = runTest {
        val existingRootKey = backupRootKey("existing-key", ByteArray(32) { it.toByte() })
        val (arrangement, useCase) = Arrangement()
            .withStoredRootKey(existingRootKey)
            .arrange()

        val result = useCase {}

        assertIs<CreateBackupFromRootKeyResult.Success>(result)
        assertEquals("A+55x3sEd0bLjE87tJ8VpBuDGCHkYRa5WbpyMx4TKiw=", arrangement.createMPBackup.receivedPassphrase)
    }

    @Test
    fun givenMPBackupCreationFails_whenCreatingBackup_thenBackupCreationFailureIsReturned() = runTest {
        val failure = CoreFailure.Unknown(null)
        val (_, useCase) = Arrangement()
            .withStoredRootKey(backupRootKey("existing-key", ByteArray(32) { 1 }))
            .withCreateMPBackupResult(CreateBackupResult.Failure(failure))
            .arrange()

        val result = useCase {}

        assertIs<CreateBackupFromRootKeyResult.Failure.BackupCreationFailed>(result)
        assertEquals(failure, result.cause)
    }

    private class Arrangement {
        val passphraseStorage = InMemoryPassphraseStorage()
        val repository = BackupRootKeyRepositoryImpl(
            selfUserId = SELF_USER_ID,
            passphraseStorage = passphraseStorage,
        )
        val createMPBackup = RecordingCreateMPBackupUseCase()
        val getOrCreateSyncedBackupRootKey = RecordingGetOrCreateSyncedBackupRootKeyUseCase(repository)
        private var generatedKeyId = "generated-key"

        fun withStoredRootKey(backupRootKey: BackupRootKey) = apply {
            repository.setBackupRootKey(backupRootKey)
        }

        fun withGeneratedKeyId(id: String) = apply {
            generatedKeyId = id
        }

        fun withCreateMPBackupResult(result: CreateBackupResult) = apply {
            createMPBackup.result = result
        }

        fun arrange(): Pair<Arrangement, CreateBackupFromRootKeyUseCase> {
            getOrCreateSyncedBackupRootKey.generatedKeyId = generatedKeyId
            return this to CreateBackupFromRootKeyUseCaseImpl(
                getOrCreateSyncedBackupRootKey = getOrCreateSyncedBackupRootKey,
                createMPBackup = createMPBackup,
                backupIdProvider = { "backup-id-1" },
            )
        }

        companion object {
            val SELF_USER_ID = QualifiedID("user", "example.com")
            val CLIENT_ID = ClientId("client-id")
        }
    }

    private class RecordingGetOrCreateSyncedBackupRootKeyUseCase(
        private val repository: BackupRootKeyRepository,
    ) : GetOrCreateSyncedBackupRootKeyUseCase {
        var generatedKeyId: String = "generated-key"

        override suspend fun invoke(): GetOrCreateSyncedBackupRootKeyResult {
            repository.getBackupRootKey()?.let {
                return GetOrCreateSyncedBackupRootKeyResult.Success(it, GetOrCreateSyncedBackupRootKeyResult.Source.LOCAL)
            }
            val generated = backupRootKey(generatedKeyId, ByteArray(32) { 1 })
            repository.setBackupRootKey(generated)
            return GetOrCreateSyncedBackupRootKeyResult.Success(generated, GetOrCreateSyncedBackupRootKeyResult.Source.GENERATED)
        }
    }

    private class RecordingCreateMPBackupUseCase : CreateMPBackupUseCase {
        var result: CreateBackupResult = CreateBackupResult.Success("testPath/backup.wbu".toPath(), "backup.wbu")
        var receivedPassphrase: String? = null

        override suspend fun invoke(password: String, onProgress: (Float) -> Unit): CreateBackupResult {
            receivedPassphrase = password
            return result
        }
    }

    private class InMemoryPassphraseStorage : PassphraseStorage {
        private val values = mutableMapOf<String, String>()

        override fun getPassphrase(key: String): String? = values[key]

        override fun setPassphrase(key: String, passphrase: String) {
            values[key] = passphrase
        }

        override fun clearPassphrase(key: String) {
            values.remove(key)
        }
    }

    private companion object {
        fun backupRootKey(id: String, keyMaterial: ByteArray): BackupRootKey =
            BackupRootKey(
                id = id,
                keyMaterial = keyMaterial,
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                createdByClientId = ClientId("client-id"),
                version = 1,
            )
    }
}
