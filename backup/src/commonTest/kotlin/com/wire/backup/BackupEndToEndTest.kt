// /*
//  * Wire
//  * Copyright (C) 2024 Wire Swiss GmbH
//  *
//  * This program is free software: you can redistribute it and/or modify
//  * it under the terms of the GNU General Public License as published by
//  * the Free Software Foundation, either version 3 of the License, or
//  * (at your option) any later version.
//  *
//  * This program is distributed in the hope that it will be useful,
//  * but WITHOUT ANY WARRANTY; without even the implied warranty of
//  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//  * GNU General Public License for more details.
//  *
//  * You should have received a copy of the GNU General Public License
//  * along with this program. If not, see http://www.gnu.org/licenses/.
//  */
// package com.wire.backup
//
// import com.wire.backup.data.BackupDateTime
// import com.wire.backup.data.BackupMessage
// import com.wire.backup.data.BackupMessageContent
// import com.wire.backup.data.BackupQualifiedId
// import com.wire.backup.dump.CommonMPBackupExporter
// import com.wire.backup.filesystem.EntryStorage
// import com.wire.backup.ingest.BackupImportResult
// import com.wire.backup.ingest.CommonMPBackupImporter
// import kotlinx.coroutines.test.runTest
// import kotlin.test.Test
// import kotlin.test.assertContentEquals
// import kotlin.test.assertEquals
// import kotlin.test.assertIs
//
// class BackupEndToEndTest {
//
//     @Test
//     fun givenBackedUpTextMessages_whenRestoring_thenShouldReadTheSameContent() = runTest {
//         shouldBackupAndRestoreSameContent(BackupMessageContent.Text("Hello from the backup!"))
//     }
//
//     @Test
//     fun givenBackedUpAssetMessage_whenRestoring_thenShouldReadTheSameContent() = runTest {
//         val content = BackupMessageContent.Asset(
//             mimeType = "image/jpeg",
//             size = 64,
//             name = "pudim.jpg",
//             otrKey = byteArrayOf(31),
//             sha256 = byteArrayOf(33),
//             assetId = "assetId",
//             assetToken = "token",
//             assetDomain = "domain",
//             encryption = BackupMessageContent.Asset.EncryptionAlgorithm.AES_GCM,
//             metaData = BackupMessageContent.Asset.AssetMetadata.Video(
//                 duration = 42,
//                 width = 800,
//                 height = 600,
//             )
//         )
//         shouldBackupAndRestoreSameContent(content)
//     }
//
//     @Test
//     fun givenBackedUpLocationMessage_whenRestoring_thenShouldReadTheSameContent() = runTest {
//         val content = BackupMessageContent.Location(
//             longitude = 42f,
//             latitude = 24f,
//             name = "Somewhere over the rainbow",
//             zoom = 13
//         )
//         shouldBackupAndRestoreSameContent(content)
//     }
//
//     private fun shouldBackupAndRestoreSameContent(content: BackupMessageContent) {
//         val expectedMessage = BackupMessage(
//             id = "messageId",
//             conversationId = BackupQualifiedId("value", "domain"),
//             senderUserId = BackupQualifiedId("senderID", "senderDomain"),
//             senderClientId = "senderClientId",
//             creationDate = BackupDateTime(0L),
//             content = content,
//         )
//         val exporter = object : CommonMPBackupExporter(BackupQualifiedId("eghyue", "potato")) {
//             override val storage: EntryStorage
//                 get() = TODO("Not yet implemented")
//         }
//         exporter.add(expectedMessage)
//         val encoded = exporter.finalize()
//
//         val importer = object : CommonMPBackupImporter() {}
//         val result = importer.importBackup(encoded)
//         assertIs<BackupImportResult.Success>(result)
//         val firstMessage = result.backupData.messages.first()
//         assertEquals(expectedMessage.conversationId, firstMessage.conversationId)
//         assertEquals(expectedMessage.id, firstMessage.id)
//         assertEquals(expectedMessage.senderClientId, firstMessage.senderClientId)
//         assertEquals(expectedMessage.senderUserId, firstMessage.senderUserId)
//         assertEquals(expectedMessage.creationDate, firstMessage.creationDate)
//         assertEquals(expectedMessage.content, firstMessage.content)
//         assertContentEquals(arrayOf(expectedMessage), result.backupData.messages)
//     }
//
//     @Test
//     fun givenBackUpDataIsUnrecognisable_whenRestoring_thenShouldReturnParsingError() = runTest {
//         val importer = object : CommonMPBackupImporter() {}
//         val result = importer.importBackup(byteArrayOf(0x42, 0x42, 0x42))
//         assertIs<BackupImportResult.ParsingFailure>(result)
//     }
// }