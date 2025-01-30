/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.backup.ingest

import com.wire.backup.data.BackupQualifiedId
import com.wire.backup.envelope.HashData
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupPeekResultTest {

    @Test
    fun givenResultWasCreatedBySameUserId_whenComparingResult_thenShouldReturnTrue() = runTest {
        val creatorUserId = BackupQualifiedId("123", "456")
        val subject = BackupPeekResult.Success("42", false, HashData.defaultFromUserId(creatorUserId))
        assertTrue { subject.isCreatedBySameUser(creatorUserId) }
    }

    @Test
    fun givenResultWasCreatedByUserIdWithDifferentDomain_whenComparingResult_thenShouldReturnFalse() = runTest {
        val creatorUserId = BackupQualifiedId("123", "456")
        val otherUserId = BackupQualifiedId("123", "789")
        val subject = BackupPeekResult.Success("42", false, HashData.defaultFromUserId(creatorUserId))
        assertFalse { subject.isCreatedBySameUser(otherUserId) }
    }

    @Test
    fun givenResultWasCreatedByDifferentUserId_whenComparingResult_thenShouldReturnFalse() = runTest {
        val creatorUserId = BackupQualifiedId("123", "456")
        val otherUserId = BackupQualifiedId("234", "456")
        val subject = BackupPeekResult.Success("42", false, HashData.defaultFromUserId(creatorUserId))
        assertFalse { subject.isCreatedBySameUser(otherUserId) }
    }
}
