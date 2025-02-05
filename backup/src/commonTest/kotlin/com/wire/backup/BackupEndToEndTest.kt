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
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BackupEndToEndTest {

    private val subject = endToEndTestSubjectProvider()

    @BeforeTest
    fun setup() {
        subject.setup()
    }

    @AfterTest
    fun beforeAfter() {
        subject.tearDown()
    }

    @Test
    fun givenBackedUpTextMessages_whenRestoring_thenShouldReadTheSameContent() = runTest {
        shouldBackupAndRestoreSameContent(BackupMessageContent.Text("Hello from the backup!"))
    }

    @Test
    fun givenBackedUpAssetMessage_whenRestoring_thenShouldReadTheSameContent() = runTest {
        val content = BackupMessageContent.Asset(
            mimeType = "image/jpeg",
            size = 64,
            name = "pudim.jpg",
            otrKey = byteArrayOf(31),
            sha256 = byteArrayOf(33),
            assetId = "assetId",
            assetToken = "token",
            assetDomain = "domain",
            encryption = BackupMessageContent.Asset.EncryptionAlgorithm.AES_GCM,
            metaData = BackupMessageContent.Asset.AssetMetadata.Video(
                duration = 42,
                width = 800,
                height = 600,
            )
        )
        shouldBackupAndRestoreSameContent(content)
    }

    @Test
    fun givenBackedUpLocationMessage_whenRestoring_thenShouldReadTheSameContent() = runTest {
        val content = BackupMessageContent.Location(
            longitude = 42f,
            latitude = 24f,
            name = "Somewhere over the rainbow",
            zoom = 13
        )
        shouldBackupAndRestoreSameContent(content)
    }

    private suspend fun shouldBackupAndRestoreSameContent(content: BackupMessageContent, password: String? = null) {
        val expectedMessage = BackupMessage(
            id = "messageId",
            conversationId = BackupQualifiedId("value", "domain"),
            senderUserId = BackupQualifiedId("senderID", "senderDomain"),
            senderClientId = "senderClientId",
            creationDate = BackupDateTime(0L),
            content = content,
        )

        val result = subject.exportImportDataTest(BackupQualifiedId("eghyue", "potato"), password) {
            add(expectedMessage)
        }

        assertIs<BackupImportResult.Success>(result)
        val pager = result.pager
        val allMessages = mutableListOf<BackupMessage>()
        while (pager.messagesPager.hasMorePages()) {
            allMessages.addAll(pager.messagesPager.nextPage())
        }
        val firstMessage = allMessages.first()
        assertEquals(expectedMessage.conversationId, firstMessage.conversationId)
        assertEquals(expectedMessage.id, firstMessage.id)
        assertEquals(expectedMessage.senderClientId, firstMessage.senderClientId)
        assertEquals(expectedMessage.senderUserId, firstMessage.senderUserId)
        assertEquals(expectedMessage.creationDate, firstMessage.creationDate)
        assertEquals(expectedMessage.content, firstMessage.content)
        assertContentEquals(arrayOf(expectedMessage), allMessages.toTypedArray())
    }
}

expect fun endToEndTestSubjectProvider(): CommonBackupEndToEndTestSubjectProvider

interface CommonBackupEndToEndTestSubjectProvider {
    fun setup() {}
    fun tearDown() {}

    suspend fun exportImportDataTest(
        selfUserId: BackupQualifiedId,
        passphrase: String?,
        export: CommonMPBackupExporter.() -> Unit,
    ): BackupImportResult
}
