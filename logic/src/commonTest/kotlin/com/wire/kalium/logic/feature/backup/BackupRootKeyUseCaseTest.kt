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

import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BackupRootKeyUseCaseTest {

    @Test
    fun givenNoStoredKey_whenGettingBackupRootKey_thenNullIsReturned() = runTest {
        val (_, getUseCase) = Arrangement().arrangeGet()

        val result = getUseCase()

        assertIs<GetBackupRootKeyResult.Success>(result)
        assertNull(result.backupRootKey)
    }

    @Test
    fun givenNoStoredKey_whenGeneratingBackupRootKey_thenKeyIsPersisted() = runTest {
        val (arrangement, generateUseCase) = Arrangement().arrangeGenerate()

        val result = generateUseCase()

        assertIs<GenerateBackupRootKeyResult.Success>(result)
        assertEquals(32, result.backupRootKey.keyMaterial.size)
        assertEquals(1, result.backupRootKey.version)
        assertEquals(Arrangement.CLIENT_ID, result.backupRootKey.createdByClientId)
        assertEquals("key-id-1", result.backupRootKey.id)
        assertNotNull(result.backupRootKey.createdAt)

        val storedKey = arrangement.repository.getBackupRootKey()
        assertNotNull(storedKey)
        assertContentEquals(result.backupRootKey.keyMaterial, storedKey.keyMaterial)
        assertEquals(result.backupRootKey.id, storedKey.id)
    }

    @Test
    fun givenStoredKey_whenGeneratingBackupRootKeyAgain_thenStoredKeyIsOverwritten() = runTest {
        val (arrangement, generateUseCase) = Arrangement().arrangeGenerate()

        val firstResult = generateUseCase()
        val secondResult = generateUseCase()

        assertIs<GenerateBackupRootKeyResult.Success>(firstResult)
        assertIs<GenerateBackupRootKeyResult.Success>(secondResult)
        assertNotEquals(firstResult.backupRootKey.id, secondResult.backupRootKey.id)
        assertEquals(secondResult.backupRootKey.id, arrangement.repository.getBackupRootKey()?.id)
    }

    @Test
    fun givenStoredKey_whenReadingBack_thenFingerprintIsStable() = runTest {
        val (arrangement, generateUseCase) = Arrangement().arrangeGenerate()
        val generated = assertIs<GenerateBackupRootKeyResult.Success>(generateUseCase()).backupRootKey

        val readBack = arrangement.repository.getBackupRootKey()

        assertNotNull(readBack)
        assertEquals(generated.fingerprint(), readBack.fingerprint())
    }

    private class Arrangement {
        val passphraseStorage = InMemoryPassphraseStorage()
        val repository = BackupRootKeyRepositoryImpl(
            selfUserId = SELF_USER_ID,
            passphraseStorage = passphraseStorage,
        )
        private var idIndex = 0

        fun arrangeGet(): Pair<Arrangement, GetBackupRootKeyUseCase> =
            this to GetBackupRootKeyUseCaseImpl(repository)

        fun arrangeGenerate(): Pair<Arrangement, GenerateBackupRootKeyUseCase> =
            this to GenerateBackupRootKeyUseCaseImpl(
                currentClientIdProvider = CurrentClientIdProvider { CLIENT_ID.right() },
                backupRootKeyRepository = repository,
                idProvider = { "key-id-${++idIndex}" },
            )

        companion object {
            val SELF_USER_ID = QualifiedID("user", "example.com")
            val CLIENT_ID = ClientId("client-id")
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
}
