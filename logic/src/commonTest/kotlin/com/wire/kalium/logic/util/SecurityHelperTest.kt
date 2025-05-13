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
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import io.mockative.Mock
import io.mockative.any
import io.mockative.doesNothing
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import io.mockative.twice
import io.mockative.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SecurityHelperTest {

    @Mock
    private val passphraseStorage: PassphraseStorage = mock(PassphraseStorage::class)

    private lateinit var securityHelper: SecurityHelper

    private val userId = UserId("df8703fb-bbab-4b10-a369-0ef781a17cf5", "wire.com")
    private val v1Alias = "mls_db_secret_alias_$userId"
    private val v2Alias = "mls_db_secret_alias_v2_$userId"

    @BeforeTest
    fun setup() {
        securityHelper = SecurityHelperImpl(passphraseStorage)
    }

    @Test
    fun whenCallingGlobalDBSecretTwice_thenTheSameValueIsReturned() {
        every {
            passphraseStorage.getPassphrase(any())
        }.returns(null)
        every { passphraseStorage.setPassphrase(any(), any()) }.doesNothing()
        val secret1 = securityHelper.globalDBSecret()
        every {
            passphraseStorage.getPassphrase(any())
        }.returns(secret1.value.encodeBase64())

        val secret2 = securityHelper.globalDBSecret()
        assertTrue(secret1.value.contentEquals(secret2.value))

        verify {
            passphraseStorage.getPassphrase(any())
        }.wasInvoked(exactly = twice)
        verify {
            passphraseStorage.setPassphrase(any(), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun whenCallingUserDBSecretTwice_thenTheSameValueIsReturned() {
        every {
            passphraseStorage.getPassphrase(any())
        }.returns(null)
        every { passphraseStorage.setPassphrase(any(), any()) }.doesNothing()
        val userId = UserId("df8703fb-bbab-4b10-a369-0ef781a17cf5", "wire.com")
        val secret1 = securityHelper.userDBSecret(userId)
        every {
            passphraseStorage.getPassphrase(any())
        }.returns(secret1.value.encodeBase64())

        val secret2 = securityHelper.userDBSecret(userId)
        assertTrue(secret1.value.contentEquals(secret2.value))
        verify {
            passphraseStorage.getPassphrase(any())
        }.wasInvoked(exactly = twice)
        verify {
            passphraseStorage.setPassphrase(any(), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun whenCallingMlsDBSecretTwiceForTheSameUser_thenTheSameValueIsReturned() {
        val userId = UserId("df8703fb-bbab-4b10-a369-0ef781a17cf5", "wire.com")

        val oldKeyBytes = ByteArray(32) { 0 }
        every { passphraseStorage.setPassphrase(any(), any()) }.doesNothing()
        setupSequencedGetPassphrase("mls_db_secret_alias_v2_$userId", listOf(oldKeyBytes.encodeBase64(), oldKeyBytes.encodeBase64()))
        setupSequencedGetPassphrase("mls_db_secret_alias_$userId", listOf(null, "oldKeyBase64"))

        val secret1 = securityHelper.mlsDBSecret(userId)
        val secret2 = securityHelper.mlsDBSecret(userId)

        assertTrue(secret1.passphrase.contentEquals(secret2.passphrase))

        verify { passphraseStorage.setPassphrase(eq("mls_db_secret_alias_$userId"), any()) }.wasInvoked(exactly = once)
    }

    @Test
    fun whenBothV1AndV2Exist_thenHasMigratedTrueAndNoNewV2Produced() {
        val v1b64 = "oldBase64"
        val v2b64 = "newBase64"

        setupSequencedGetPassphrase(v2Alias, listOf(v2b64))
        setupSequencedGetPassphrase(v1Alias, listOf(v1b64))

        every { passphraseStorage.setPassphrase(any(), any()) }.doesNothing()

        val secret = securityHelper.mlsDBSecret(userId)

        assertTrue(secret.hasMigrated)
        assertEquals(v1b64, secret.value)
        assertTrue(secret.passphrase.contentEquals(v2b64.decodeBase64Bytes()))
        verify { passphraseStorage.setPassphrase(any(), any()) }.wasNotInvoked()
    }

    @Test
    fun whenV2ExistsButV1Absent_thenGeneratesOnlyV1() {
        val v2b64 = "newBase64"
        val captured = mutableListOf<String>()

        setupSequencedGetPassphrase(v2Alias, listOf(v2b64))
        setupSequencedGetPassphrase(v1Alias, listOf(null, "generatedOldB64"))
        setupSequencedSetPassphrase(v1Alias, captured)

        val secret = securityHelper.mlsDBSecret(userId)

        assertTrue(secret.hasMigrated)
        assertTrue(secret.passphrase.contentEquals(v2b64.decodeBase64Bytes()))
        assertEquals(1, captured.size)
    }

    private fun setupSequencedSetPassphrase(key: String, capturedValues: MutableList<String>) = apply {
        every { passphraseStorage.setPassphrase(any(), any()) }
            .invokes { args ->
                val receivedKey = args[0] as String
                val receivedValue = args[1] as String
                if (receivedKey == key) {
                    capturedValues.add(receivedValue)
                }
            }
    }

    private fun setupSequencedGetPassphrase(key: String, returnList: List<String?>) = apply {
        var invocationCounter = 0
        every { passphraseStorage.getPassphrase(key) }
            .invokes { _ ->
                if (invocationCounter < returnList.size) {
                    val returnValue = returnList[invocationCounter]
                    invocationCounter++
                    returnValue
                } else {
                    null
                }
            }
    }
}
