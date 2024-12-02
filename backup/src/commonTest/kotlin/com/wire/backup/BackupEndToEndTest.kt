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
package com.wire.backup

import com.wire.backup.data.BackupDateTime
import com.wire.backup.data.BackupMessage
import com.wire.backup.data.BackupMessageContent
import com.wire.backup.data.BackupQualifiedId
import com.wire.backup.dump.CommonMPBackupExporter
import com.wire.backup.ingest.BackupImportResult
import com.wire.backup.ingest.CommonMPBackupImporter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertIs

class BackupEndToEndTest {

    @Test
    fun givenBackedUpMessages_whenRestoring_thenShouldReadTheSameContent() = runTest {
        val expectedMessage = BackupMessage(
            "messageId",
            BackupQualifiedId("value", "domain"),
            BackupQualifiedId("senderID", "senderDomain"),
            "senderClientId",
            BackupDateTime(24232L),
            BackupMessageContent.Text("Hello from the backup!")
        )
        val exporter = object : CommonMPBackupExporter(BackupQualifiedId("eghyue", "potato")) {}
        exporter.addMessage(expectedMessage)
        val encoded = exporter.serialize()

        val importer = object : CommonMPBackupImporter("potato") {}
        val result = importer.importBackup(encoded)
        assertIs<BackupImportResult.Success>(result)
        assertContentEquals(arrayOf(expectedMessage), result.backupData.messages)
    }

    @Test
    fun givenBackUpDataIsUnrecognisable_whenRestoring_thenShouldReturnParsingError() = runTest {
        val importer = object : CommonMPBackupImporter("potato") {}
        val result = importer.importBackup(byteArrayOf(0x42, 0x42, 0x42))
        assertIs<BackupImportResult.ParsingFailure>(result)
    }
}
