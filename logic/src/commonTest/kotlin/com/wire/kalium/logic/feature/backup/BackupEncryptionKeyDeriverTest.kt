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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class BackupEncryptionKeyDeriverTest {

    @Test
    fun givenSameRootKeyAndBackupId_whenDerivingPassphrase_thenResultIsStable() {
        val rootKey = ByteArray(32) { it.toByte() }

        val first = HkdfBackupEncryptionKeyDeriver.deriveBase64Passphrase(rootKey, "backup-id-1")
        val second = HkdfBackupEncryptionKeyDeriver.deriveBase64Passphrase(rootKey, "backup-id-1")

        assertEquals("A+55x3sEd0bLjE87tJ8VpBuDGCHkYRa5WbpyMx4TKiw=", first)
        assertEquals(first, second)
    }

    @Test
    fun givenDifferentBackupId_whenDerivingPassphrase_thenResultIsDifferent() {
        val rootKey = ByteArray(32) { it.toByte() }

        val first = HkdfBackupEncryptionKeyDeriver.deriveBase64Passphrase(rootKey, "backup-id-1")
        val second = HkdfBackupEncryptionKeyDeriver.deriveBase64Passphrase(rootKey, "backup-id-2")

        assertEquals("InkgnLgXcTuMUKKYs7+BtfCBuPvAUB6puSEeX/PCHHI=", second)
        assertNotEquals(first, second)
    }
}
