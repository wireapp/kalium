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

package com.wire.kalium.persistence.secret

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AndroidBootstrapSecretStoreTest {

    private lateinit var context: Context
    private lateinit var cipher: FakeBootstrapSecretCipher
    private lateinit var store: AndroidBootstrapSecretStore

    @BeforeTest
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        cipher = FakeBootstrapSecretCipher()
        store = AndroidBootstrapSecretStore(
            context = context,
            cipher = cipher,
            fileName = TEST_FILE_NAME
        )
        store.clearGlobalDbPassphrase()
    }

    @AfterTest
    fun tearDown() {
        store.clearGlobalDbPassphrase()
    }

    @Test
    fun givenNoBootstrapSecret_whenGettingOrCreatingPassphrase_thenCreatesAndPersistsThirtyTwoByteSecret() {
        val createdPassphrase = store.getOrCreateGlobalDbPassphrase()
        val loadedPassphrase = assertNotNull(store.getGlobalDbPassphrase())

        assertEquals(GLOBAL_DB_PASSPHRASE_SIZE, createdPassphrase.size)
        assertContentEquals(createdPassphrase, loadedPassphrase)
    }

    @Test
    fun givenExistingBootstrapSecret_whenUsingScopedPassphrase_thenPassphraseIsZeroizedAfterUse() {
        val originalPassphrase = ByteArray(GLOBAL_DB_PASSPHRASE_SIZE) { it.toByte() }
        store.putGlobalDbPassphrase(originalPassphrase)

        var scopedPassphrase: ByteArray? = null
        store.withGlobalDbPassphrase {
            scopedPassphrase = it
            assertContentEquals(originalPassphrase, it)
        }

        val zeroizedPassphrase = assertNotNull(scopedPassphrase)
        assertTrue(zeroizedPassphrase.all { it == 0.toByte() })
    }

    @Test
    fun givenShortPassphrase_whenSavingGlobalDbPassphrase_thenThrows() {
        assertFailsWith<IllegalArgumentException> {
            store.putGlobalDbPassphrase(ByteArray(GLOBAL_DB_PASSPHRASE_SIZE - 1))
        }
    }

    private class FakeBootstrapSecretCipher : BootstrapSecretCipher {
        override val keyAlias: String = "test-key-alias"

        override fun encrypt(plainText: ByteArray, aad: ByteArray): EncryptedBootstrapSecret =
            EncryptedBootstrapSecret(
                iv = ByteArray(TEST_IV_SIZE) { (it + 1).toByte() },
                cipherText = plainText.xor(aad),
                isStrongBoxBacked = false
            )

        override fun decrypt(encryptedSecret: EncryptedBootstrapSecret, aad: ByteArray): ByteArray =
            encryptedSecret.cipherText.xor(aad)

        private fun ByteArray.xor(aad: ByteArray): ByteArray =
            mapIndexed { index, byte ->
                (byte.toInt() xor aad[index % aad.size].toInt()).toByte()
            }.toByteArray()
    }

    private companion object {
        const val GLOBAL_DB_PASSPHRASE_SIZE = 32
        const val TEST_IV_SIZE = 12
        const val TEST_FILE_NAME = "bootstrap-secrets-test.json"
    }
}
