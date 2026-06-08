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

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class BackupRootKeyExportEncryptorTest {

    @Test
    fun givenRootKeyData_whenEncrypting_thenEnvelopeDoesNotContainRawKeyMaterial() = runTest {
        val envelope = BackupRootKeyExportEncryptor.encrypt(EXPORT_DATA, PASSWORD)
        val serializedEnvelope = BackupRootKeyExportEncryptor.encodeEnvelope(envelope)

        assertEquals(BackupRootKeyExportEncryptor.FORMAT, envelope.format)
        assertEquals(BackupRootKeyExportEncryptor.VERSION, envelope.version)
        assertEquals(BackupRootKeyExportEncryptor.ENCRYPTION_ALGORITHM, envelope.encryptionAlgorithm)
        assertFalse(serializedEnvelope.contains(EXPORT_DATA.keyMaterial))
    }

    @Test
    fun givenRootKeyData_whenDecryptingWithSamePassword_thenOriginalDataIsRecovered() = runTest {
        val envelope = BackupRootKeyExportEncryptor.encrypt(EXPORT_DATA, PASSWORD)

        val result = BackupRootKeyExportEncryptor.decrypt(envelope, PASSWORD)

        assertIs<BackupRootKeyDecryptResult.Success>(result)
        assertEquals(EXPORT_DATA, result.data)
    }

    @Test
    fun givenRootKeyData_whenDecryptingWithWrongPassword_thenAuthenticationFails() = runTest {
        val envelope = BackupRootKeyExportEncryptor.encrypt(EXPORT_DATA, PASSWORD)

        val result = BackupRootKeyExportEncryptor.decrypt(envelope, "wrong-password")

        assertEquals(BackupRootKeyDecryptResult.AuthenticationFailure, result)
    }

    private companion object {
        const val PASSWORD = "account-password"
        val EXPORT_DATA = BackupRootKeyExportData(
            userId = "user@wire.com",
            rootKeyId = "root-key-id",
            rootKeyVersion = 1,
            rootKeyFingerprint = "AA:BB:CC:DD",
            createdAt = "2026-06-06T12:00:00Z",
            createdByClientId = "client-id",
            keyMaterial = "AQIDBAUGBwgJCgsMDQ4PEA==",
        )
    }
}
