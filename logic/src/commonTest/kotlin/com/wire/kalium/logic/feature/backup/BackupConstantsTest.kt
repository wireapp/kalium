/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.util.DateTimeUtil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BackupConstantsTest {
    @Test
    fun givenSomeValidData_whenCreatingNonEncryptedBackup_thenTheFinalBackupFileIsCreatedCorrectly() = runTest() {
        // Given
        val timestamp = DateTimeUtil.currentSimpleDateTimeString()
        val userHandle = "user_handle"

        // When
        val backupFileName = BackupConstants.createBackupFileName(userHandle, timestamp)
        println(backupFileName)

        // Then
        assertTrue(isValidFileName(backupFileName))
    }

    private fun isValidFileName(filename: String): Boolean {
        val regex = """^[a-zA-Z0-9_-]+\.[a-zA-Z0-9_]+$""".toRegex()
        return regex.matches(filename)
    }
}
