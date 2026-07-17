/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.logic.util

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verify
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SecurityHelperTest {

        private val passphraseStorage: PassphraseStorage = mock(mode = MockMode.autoUnit)

    private lateinit var securityHelper: SecurityHelper

    private val userId = UserId("df8703fb-bbab-4b10-a369-0ef781a17cf5", "wire.com")
    private val globalV1Alias = "global_db_passphrase_alias"
    private val globalV2Alias = "global_db_passphrase_alias_v2"
    private val globalV2PendingAlias = "global_db_passphrase_alias_v2_pending"
    private val userV1Alias = "user_db_secret_alias_$userId"
    private val userV2Alias = "user_db_secret_alias_v2_$userId"
    private val mlsV1Alias = "mls_db_secret_alias_$userId"
    private val mlsV2Alias = "mls_db_secret_alias_v2_$userId"
    private val proteusV1Alias = "proteus_db_secret_alias_$userId"
    private val proteusV2Alias = "proteus_db_secret_alias_v2_$userId"
    private val rootPath = "/root/path"

    @BeforeTest
    fun setup() {
        // Use a no-op migrator by default for tests to avoid file system dependencies
        val noOpMigrator: DatabaseMigrator = { _, _, _ -> }
        securityHelper = SecurityHelperImpl(passphraseStorage, noOpMigrator)
    }

    @Test
    fun whenCallingGlobalDBSecretTwice_thenTheSameValueIsReturned() = runTest {
        every {
            passphraseStorage.getPassphrase(any())
        }.returns(null)
        every { passphraseStorage.setPassphrase(any(), any()) } returns Unit
        val secret1 = securityHelper.globalDBSecret()
        every {
            passphraseStorage.getPassphrase(any())
        }.returns(Base64.encode(secret1.value))

        val secret2 = securityHelper.globalDBSecret()
        assertTrue(secret1.value.contentEquals(secret2.value))

        verify(VerifyMode.exactly(2)) {
            passphraseStorage.getPassphrase(any())
        }
        verify(VerifyMode.exactly(1)) {
            passphraseStorage.setPassphrase(any(), any())
        }
    }

    @Test
    fun whenCallingUserDBSecretTwice_thenTheSameValueIsReturned() = runTest {
        every {
            passphraseStorage.getPassphrase(any())
        }.returns(null)
        every { passphraseStorage.setPassphrase(any(), any()) } returns Unit
        val userId = UserId("df8703fb-bbab-4b10-a369-0ef781a17cf5", "wire.com")
        val secret1 = securityHelper.userDBSecret(userId)
        every {
            passphraseStorage.getPassphrase(any())
        }.returns(Base64.encode(secret1.value))

        val secret2 = securityHelper.userDBSecret(userId)
        assertTrue(secret1.value.contentEquals(secret2.value))
        verify(VerifyMode.exactly(2)) {
            passphraseStorage.getPassphrase(any())
        }
        verify(VerifyMode.exactly(1)) {
            passphraseStorage.setPassphrase(any(), any())
        }
    }

    @Test
    fun givenExistingUserDatabaseAndOnlyV1Alias_whenGettingSecret_thenLegacySecretIsReturned() {
        val secretBytes = ByteArray(32) { it.toByte() }
        every { passphraseStorage.getPassphrase(userV2Alias) } returns null
        every { passphraseStorage.getPassphrase(userV1Alias) } returns Base64.encode(secretBytes)

        val secret = securityHelper.userDBSecret(userId, databaseExists = true)

        assertTrue(secret.value.contentEquals(secretBytes))
        verify(VerifyMode.not) { passphraseStorage.setPassphrase(any(), any()) }
    }

    @Test
    fun givenNewUserDatabaseAndNoV2Alias_whenGettingSecret_thenNewV2RawSecretIsGenerated() {
        every { passphraseStorage.getPassphrase(userV2Alias) } returns null
        every { passphraseStorage.setPassphrase(any(), any()) } returns Unit

        val secret = securityHelper.userDBSecret(userId, databaseExists = false)

        assertEquals(67, secret.value.size)
        assertEquals('x'.code.toByte(), secret.value.first())
        assertEquals('\''.code.toByte(), secret.value.last())
        verify(VerifyMode.exactly(1)) { passphraseStorage.setPassphrase(eq(userV2Alias), any()) }
        verify(VerifyMode.not) { passphraseStorage.getPassphrase(userV1Alias) }
    }

    @Test
    fun givenV2UserAlias_whenGettingSecret_thenStoredSecretIsReturnedAsRawKey() {
        val secretBytes = ByteArray(32) { it.toByte() }
        every { passphraseStorage.getPassphrase(userV2Alias) } returns Base64.encode(secretBytes)

        val secret = securityHelper.userDBSecret(userId, databaseExists = true)

        assertEquals(
            "x'000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f'",
            secret.value.decodeToString()
        )
        verify(VerifyMode.not) { passphraseStorage.getPassphrase(userV1Alias) }
    }

    @Test
    fun givenV2UserAlias_whenGettingOptionalSecret_thenRawSecretIsReturnedForDatabaseExport() {
        val secretBytes = ByteArray(32) { it.toByte() }
        every { passphraseStorage.getPassphrase(userV2Alias) } returns Base64.encode(secretBytes)

        val secret = securityHelper.userDBOrSecretNull(userId)

        assertEquals(
            "x'000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f'",
            secret?.value?.decodeToString()
        )
        verify(VerifyMode.not) { passphraseStorage.getPassphrase(userV1Alias) }
    }

    @Test
    fun givenExistingGlobalDatabaseAndOnlyV1Alias_whenGettingKeyMaterial_thenFreshPendingV2IsReturned() {
        val v1SecretBytes = "historical-v1-secret".encodeToByteArray()
        val pendingV2SecretBytes = ByteArray(32) { (it + 1).toByte() }
        every { passphraseStorage.getPassphrase(globalV2Alias) } returns null
        every { passphraseStorage.getPassphrase(globalV2PendingAlias) } returns Base64.encode(pendingV2SecretBytes)
        every { passphraseStorage.getPassphrase(globalV1Alias) } returns Base64.encode(v1SecretBytes)

        val keyMaterial = securityHelper.globalDBKeyMaterial(databaseExists = true)

        assertTrue(keyMaterial.currentSecret.value.contentEquals(v1SecretBytes))
        assertEquals(
            "x'0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20'",
            keyMaterial.migrationRawKey?.decodeToString()
        )
    }

    @Test
    fun givenCompletedGlobalMigration_whenMarkingV2_thenPendingSecretIsPromotedToV2Alias() {
        val pendingSecret = Base64.encode(ByteArray(32) { it.toByte() })
        every { passphraseStorage.getPassphrase(globalV2Alias) } returns null
        every { passphraseStorage.getPassphrase(globalV2PendingAlias) } returns pendingSecret
        every { passphraseStorage.setPassphrase(any(), any()) } returns Unit
        every { passphraseStorage.clearPassphrase(any()) } returns Unit

        securityHelper.markGlobalDBSecretAsV2()

        verify(VerifyMode.exactly(1)) {
            passphraseStorage.setPassphrase(globalV2Alias, pendingSecret)
        }
        verify(VerifyMode.exactly(1)) { passphraseStorage.clearPassphrase(globalV2PendingAlias) }
    }

    @Test
    fun givenNewGlobalDatabaseAndNoV2Alias_whenGettingSecret_thenNewV2RawSecretIsGenerated() {
        every { passphraseStorage.getPassphrase(globalV2Alias) } returns null
        every { passphraseStorage.setPassphrase(any(), any()) } returns Unit

        val keyMaterial = securityHelper.globalDBKeyMaterial(databaseExists = false)

        assertEquals(67, keyMaterial.currentSecret.value.size)
        assertEquals(null, keyMaterial.migrationRawKey)
        verify(VerifyMode.exactly(1)) { passphraseStorage.setPassphrase(eq(globalV2Alias), any()) }
        verify(VerifyMode.not) { passphraseStorage.getPassphrase(globalV1Alias) }
    }

    @Test
    fun whenCallingMlsDBSecretTwiceForTheSameUser_thenTheSameValueIsReturned() = runTest {
        val userId = UserId("df8703fb-bbab-4b10-a369-0ef781a17cf5", "wire.com")

        val oldKeyBytes = ByteArray(32) { 0 }
        every { passphraseStorage.setPassphrase(any(), any()) } returns Unit
        setupSequencedGetPassphrase(mlsV2Alias, listOf(Base64.encode(oldKeyBytes), Base64.encode(oldKeyBytes)))
        setupSequencedGetPassphrase(mlsV1Alias, listOf(null, "oldKeyBase64"))

        val secret1 = securityHelper.mlsDBSecret(userId, rootPath)
        val secret2 = securityHelper.mlsDBSecret(userId, rootPath)

        assertTrue(secret1.passphrase.contentEquals(secret2.passphrase))

        verify(VerifyMode.not) { passphraseStorage.setPassphrase(any(), any()) }
    }

    @Test
    fun givenV2Exists_whenCallingMlsDBSecret_thenReturnsV2WithoutMigration() = runTest {
        val v2b64 = Base64.encode("newBase64".encodeToByteArray())

        setupSequencedGetPassphrase(mlsV2Alias, listOf(v2b64))
        every { passphraseStorage.setPassphrase(any(), any()) } returns Unit

        val secret = securityHelper.mlsDBSecret(userId, rootPath)

        assertTrue(secret.passphrase.contentEquals(Base64.decode(v2b64)))
        verify(VerifyMode.not) { passphraseStorage.setPassphrase(any(), any()) }
    }

    @Test
    fun givenOnlyV1Exists_whenCallingMlsDBSecret_thenMigratesToV2() = runTest {
        val v1b64 = "oldBase64"
        val captured = mutableListOf<String>()
        
        val tempDir = "/tmp/test"
        setupSequencedGetPassphrase(mlsV2Alias, listOf(null))
        setupSequencedGetPassphrase(mlsV1Alias, listOf(v1b64))
        setupSequencedSetPassphrase(mlsV2Alias, captured)

        val secret = securityHelper.mlsDBSecret(userId, tempDir)
        
        assertEquals(32, secret.passphrase.size)
        assertEquals(1, captured.size)
        assertTrue(captured[0].isNotEmpty())
    }

    private fun setupSequencedSetPassphrase(key: String, capturedValues: MutableList<String>) = apply {
        every { passphraseStorage.setPassphrase(any(), any()) }
            .calls {
                val receivedKey = it.args[0] as String
                val receivedValue = it.args[1] as String
                if (receivedKey == key) {
                    capturedValues.add(receivedValue)
                }
            }
    }

    private fun setupSequencedGetPassphrase(key: String, returnList: List<String?>) = apply {
        var invocationCounter = 0
        every { passphraseStorage.getPassphrase(key) }
            .calls {
                if (invocationCounter < returnList.size) {
                    val returnValue = returnList[invocationCounter]
                    invocationCounter++
                    returnValue
                } else {
                    null
                }
            }
    }

    @Test
    fun givenNeitherV1NorV2Exist_whenCallingMlsDBSecret_thenGeneratesNewV2Secret() = runTest {
        setupSequencedGetPassphrase(mlsV2Alias, listOf(null, null))
        setupSequencedGetPassphrase(mlsV1Alias, listOf(null))
        every { passphraseStorage.setPassphrase(any(), any()) } returns Unit

        val secret = securityHelper.mlsDBSecret(userId, rootPath)

        assertEquals(32, secret.passphrase.size)
        verify(VerifyMode.exactly(1)) { passphraseStorage.setPassphrase(eq(mlsV2Alias), any()) }
    }

    // PROTEUS DB SECRET TESTS

    @Test
    fun givenV2Exists_whenCallingProteusDBSecret_thenReturnsV2WithoutMigration() = runTest {
        val v2b64 = Base64.encode("proteusV2Secret".encodeToByteArray())

        setupSequencedGetPassphrase(proteusV2Alias, listOf(v2b64))
        every { passphraseStorage.setPassphrase(any(), any()) } returns Unit

        val secret = securityHelper.proteusDBSecret(userId, rootPath)

        assertTrue(secret.passphrase.contentEquals(Base64.decode(v2b64)))
        verify(VerifyMode.not) { passphraseStorage.setPassphrase(any(), any()) }
    }

    @Test
    fun givenOnlyV1Exists_whenCallingProteusDBSecret_thenMigratesToV2() = runTest {
        val v1b64 = "proteusV1Secret"
        val captured = mutableListOf<String>()
        
        val tempDir = "/tmp/test"
        setupSequencedGetPassphrase(proteusV2Alias, listOf(null))
        setupSequencedGetPassphrase(proteusV1Alias, listOf(v1b64))
        setupSequencedSetPassphrase(proteusV2Alias, captured)

        val secret = securityHelper.proteusDBSecret(userId, tempDir)
        
        assertEquals(32, secret.passphrase.size)
        assertEquals(1, captured.size)
        assertTrue(captured[0].isNotEmpty())
    }

    @Test
    fun givenNeitherV1NorV2Exist_whenCallingProteusDBSecret_thenGeneratesNewV2Secret() = runTest {
        setupSequencedGetPassphrase(proteusV2Alias, listOf(null, null))
        setupSequencedGetPassphrase(proteusV1Alias, listOf(null))
        every { passphraseStorage.setPassphrase(any(), any()) } returns Unit

        val secret = securityHelper.proteusDBSecret(userId, rootPath)

        assertEquals(32, secret.passphrase.size)
        verify(VerifyMode.exactly(1)) { passphraseStorage.setPassphrase(eq(proteusV2Alias), any()) }
    }

    @Test
    fun whenCallingProteusDBSecretTwiceForSameUser_thenReturnsSameValue() = runTest {
        val secretB64 = Base64.encode("proteusSecretValue".encodeToByteArray())
        setupSequencedGetPassphrase(proteusV2Alias, listOf(secretB64, secretB64))
        every { passphraseStorage.setPassphrase(any(), any()) } returns Unit

        val secret1 = securityHelper.proteusDBSecret(userId, rootPath)
        val secret2 = securityHelper.proteusDBSecret(userId, rootPath)

        assertTrue(secret1.passphrase.contentEquals(secret2.passphrase))
    }

    @Test
    fun givenV1ExistsForMls_whenCallingMlsDBSecret_thenGeneratesNewKeyAndStoresV2() = runTest {
        val v1Secret = "oldMlsSecret"
        val capturedV2Keys = mutableListOf<String>()
        
        val tempDir = "/tmp/test"
        setupSequencedGetPassphrase(mlsV2Alias, listOf(null))
        setupSequencedGetPassphrase(mlsV1Alias, listOf(v1Secret))
        setupSequencedSetPassphrase(mlsV2Alias, capturedV2Keys)

        val secret = securityHelper.mlsDBSecret(userId, tempDir)
        
        // Verify new key was generated (32 bytes)
        assertEquals(32, secret.passphrase.size)
        // Verify v2 key was stored
        assertEquals(1, capturedV2Keys.size)
        // Verify stored key is base64 encoded
        assertTrue(capturedV2Keys[0].isNotEmpty())
        // Verify the returned secret matches what was stored
        assertTrue(secret.passphrase.contentEquals(Base64.decode(capturedV2Keys[0])))
    }

    @Test
    fun givenV1ExistsForProteus_whenCallingProteusDBSecret_thenGeneratesNewKeyAndStoresV2() = runTest {
        val v1Secret = "oldProteusSecret"
        val capturedV2Keys = mutableListOf<String>()
        
        val tempDir = "/tmp/test"
        setupSequencedGetPassphrase(proteusV2Alias, listOf(null))
        setupSequencedGetPassphrase(proteusV1Alias, listOf(v1Secret))
        setupSequencedSetPassphrase(proteusV2Alias, capturedV2Keys)

        val secret = securityHelper.proteusDBSecret(userId, tempDir)
        
        // Verify new key was generated (32 bytes)
        assertEquals(32, secret.passphrase.size)
        // Verify v2 key was stored
        assertEquals(1, capturedV2Keys.size)
        // Verify stored key is base64 encoded
        assertTrue(capturedV2Keys[0].isNotEmpty())
        // Verify the returned secret matches what was stored
        assertTrue(secret.passphrase.contentEquals(Base64.decode(capturedV2Keys[0])))
    }
}
