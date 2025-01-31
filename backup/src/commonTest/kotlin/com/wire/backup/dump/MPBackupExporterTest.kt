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
package com.wire.backup.dump

import com.wire.backup.data.BackupQualifiedId
import com.wire.backup.filesystem.BackupPage
import com.wire.backup.filesystem.BackupPageStorage
import com.wire.backup.filesystem.InMemoryBackupPageStorage
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MPBackupExporterTest {

    @Test
    fun givenZippingError_whenFinalizing_thenZipErrorShouldBeReturned() = runTest {
        val thrownException = IllegalStateException("Zipping failed!")
        val subject = object : CommonMPBackupExporter(
            BackupQualifiedId("user", "domain")
        ) {
            override val storage: BackupPageStorage = InMemoryBackupPageStorage()

            override fun zipEntries(data: List<BackupPage>): Deferred<Source> {
                throw thrownException
            }
        }

        val result = subject.finalize(null, Buffer())
        assertIs<ExportResult.Failure.ZipError>(result)
        assertEquals(thrownException.message, result.message)
    }

}
