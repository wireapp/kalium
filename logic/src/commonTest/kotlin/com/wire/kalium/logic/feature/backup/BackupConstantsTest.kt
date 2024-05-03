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

package com.wire.kalium.logic.feature.backup

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BackupConstantsTest {
    @Test
    fun givenTimeStampWithColons_whenCreatingFileNameForBackup_thenShouldReplaceColons() = runTest() {
        // Given
        val timestamp = "10:20:40.000"
        val correctedTimestamp = "10-20-40.000"
        val userHandle = "user_handle"

        // When
        val backupFileName = BackupConstants.createBackupFileName(userHandle, timestamp)
        // Then
        assertFalse(backupFileName.contains(":"))
        assertTrue(backupFileName.contains(correctedTimestamp))
    }
}
