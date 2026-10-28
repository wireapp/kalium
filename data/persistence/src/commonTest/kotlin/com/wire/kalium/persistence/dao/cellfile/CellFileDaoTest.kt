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
package com.wire.kalium.persistence.dao.cellfile

import app.cash.sqldelight.async.coroutines.awaitAsOne
import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.dao.message.attachment.MessageAttachmentEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CellFileDaoTest : BaseDatabaseTest() {

    private lateinit var database: UserDatabaseBuilder
    private lateinit var cellFileDao: CellFileDao
    private val selfUserId = UserIDEntity("selfId", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        database = createDatabase(selfUserId, encryptedDBSecret, true)
        cellFileDao = database.cellFileDao
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun givenOfflineFile_whenUpsertedWithIsOfflineFalse_thenOfflineStatusIsPreserved() = runTest(dispatcher) {
        val uuid = "test-asset-id"

        cellFileDao.upsert(
            CellFileEntity(
                uuid = uuid,
                conversationId = "conv@domain",
                name = "file.pdf",
                owner = null,
                localPath = "/data/file.pdf",
                size = 1024,
                downloadedAt = 1000L,
                isOffline = true,
            )
        )

        cellFileDao.upsert(
            CellFileEntity(
                uuid = uuid,
                conversationId = "conv@domain",
                name = "file.pdf",
                owner = null,
                localPath = "/data/file.pdf",
                size = 1024,
                downloadedAt = 2000L,
                isOffline = false,
            )
        )

        val result = cellFileDao.getById(uuid)
        assertEquals(result?.isOffline, true)
    }

    @Test
    fun givenFilesInDb_whenGettingLocalPathsByIds_thenOnlyRequestedFilesWithLocalPathAreReturned() = runTest(dispatcher) {
        cellFileDao.upsert(cellFile(uuid = "requested", localPath = "/data/requested"))
        cellFileDao.upsert(cellFile(uuid = "requested_no_path", localPath = null))
        cellFileDao.upsert(cellFile(uuid = "not_requested", localPath = "/data/not_requested"))

        val result = cellFileDao.getLocalPathsByIds(listOf("requested", "requested_no_path", "missing"))

        assertEquals(listOf(CellFileLocalPath("requested", "/data/requested")), result)
    }

    @Test
    fun givenEmptyIds_whenGettingLocalPathsByIds_thenEmptyListIsReturned() = runTest(dispatcher) {
        cellFileDao.upsert(cellFile(uuid = "file", localPath = "/data/file"))

        val result = cellFileDao.getLocalPathsByIds(emptyList())

        assertEquals(emptyList(), result)
    }

    private fun cellFile(uuid: String, localPath: String?) = CellFileEntity(
        uuid = uuid,
        conversationId = "conv@domain",
        name = "$uuid.pdf",
        owner = null,
        localPath = localPath,
        size = 1024,
        downloadedAt = 1000L,
        isOffline = false,
    )

    @Test
    fun givenLegacyOfflineFile_whenInserted_thenRemotePathIsNull() = runTest(dispatcher) {
        val file = offlineFile()
        cellFileDao.upsert(file)

        assertEquals(file, cellFileDao.getById(file.uuid))
        assertNull(database.database.cellFilesQueries.selectById(file.uuid).awaitAsOne().assetPath)
    }

    @Test
    fun givenRemotePath_whenInserted_thenAllOfflineReadsReturnIt() = runTest(dispatcher) {
        val file = offlineFile("conv/reports/été #1.pdf")
        cellFileDao.upsert(file)

        assertEquals(file, cellFileDao.getById(file.uuid))
        assertEquals(listOf(file), cellFileDao.observeOfflineFiles().first())
        assertEquals(listOf(file), cellFileDao.observeOfflineFilesByConversationId(file.conversationId).first())
        assertEquals(file.remotePath, database.database.cellFilesQueries.selectById(file.uuid).awaitAsOne().assetPath)
    }

    @Test
    fun givenRemotePath_whenUpdated_thenNewPathReplacesItAndNullPreservesIt() = runTest(dispatcher) {
        val file = offlineFile()
        cellFileDao.upsert(file)
        cellFileDao.upsert(file.copy(remotePath = "conv/old/file.pdf"))
        val moved = file.copy(remotePath = "conv/new/file.pdf", downloadedAt = 2000L)
        cellFileDao.upsert(moved)
        cellFileDao.upsert(moved.copy(remotePath = null, isOffline = false))

        assertEquals(moved, cellFileDao.getById(file.uuid))
        assertEquals(listOf(moved), cellFileDao.observeOfflineFiles().first())
    }

    @Test
    fun givenExistingAttachmentPath_whenSavedOfflineWithoutPath_thenAttachmentPathIsPreserved() = runTest(dispatcher) {
        // An attachment can already own assetPath before the offline save API is called.
        database.sqlDriver.execute(
            null,
            "INSERT INTO CellFile(uuid, downloadedAt, assetPath) VALUES ('file-id', 0, 'conv/reports/file.pdf')",
            0,
        ).await()
        val file = offlineFile()
        cellFileDao.upsert(file)

        assertEquals(file.copy(remotePath = "conv/reports/file.pdf"), cellFileDao.getById(file.uuid))
    }

    @Test
    fun givenOfflinePaths_whenDatabaseReopenedAndOfflineAccessCleared_thenPathsRemainStored() = runTest(dispatcher) {
        val file = offlineFile("conv/reports/file.pdf")
        val legacy = offlineFile().copy(uuid = "legacy-file")
        cellFileDao.upsert(file)
        cellFileDao.upsert(legacy)
        database.close()
        database = createDatabase(selfUserId, encryptedDBSecret, true)
        cellFileDao = database.cellFileDao

        assertEquals(file, cellFileDao.getById(file.uuid))
        assertEquals(legacy, cellFileDao.getById(legacy.uuid))
        cellFileDao.clearOfflineAccess(file.uuid)
        val cleared = checkNotNull(cellFileDao.getById(file.uuid))
        assertFalse(cleared.isOffline)
        assertNull(cleared.localPath)
        assertEquals(file.remotePath, cleared.remotePath)
        assertEquals(listOf(legacy), cellFileDao.observeOfflineFiles().first())
    }

    @Test
    fun givenStandaloneFile_whenDeletedAsStandalone_thenItIsRemoved() = runTest(dispatcher) {
        cellFileDao.upsert(offlineFile("conv/reports/file.pdf"))

        cellFileDao.deleteStandalone("file-id")

        assertNull(cellFileDao.getById("file-id"))
    }

    @Test
    fun givenFileSharedAsAttachment_whenDeletedAsStandalone_thenTheAttachmentIsKeptWithoutLocalCopy() = runTest(dispatcher) {
        insertMessageWithAttachment(assetId = "attached", assetPath = "conv/reports/attached.pdf")

        cellFileDao.deleteStandalone("attached")

        // Deleting the row would have removed the attachment from its message.
        val attachment = database.messageAttachments.getAttachment("attached")
        assertNull(attachment.localPath)
    }

    @Test
    fun givenFilesUnderAFolder_whenDeletedUnderFolder_thenAllDepthsAreRemovedAndTheirLocalCopiesReturned() = runTest(dispatcher) {
        cellFileDao.upsert(fileAt("direct", "conv/folder/a.pdf", localPath = "/data/a.pdf"))
        cellFileDao.upsert(fileAt("nested", "conv/folder/sub/b.pdf", localPath = "/data/b.pdf"))
        cellFileDao.upsert(fileAt("not_downloaded", "conv/folder/c.pdf", localPath = null))
        cellFileDao.upsert(fileAt("sibling", "conv/folder2/d.pdf", localPath = "/data/d.pdf"))
        // '-' sorts before '/' and '0' right after it: the edges of the range the folder is matched with.
        cellFileDao.upsert(fileAt("sibling_before", "conv/folder-old/f.pdf", localPath = "/data/f.pdf"))
        cellFileDao.upsert(fileAt("sibling_after", "conv/folder0/g.pdf", localPath = "/data/g.pdf"))
        cellFileDao.upsert(fileAt("outside", "conv/e.pdf", localPath = "/data/e.pdf"))

        val localPaths = cellFileDao.deleteUnderFolder("conv/folder", "NOT_FOUND")

        assertEquals(setOf("/data/a.pdf", "/data/b.pdf"), localPaths.map { it.localPath }.toSet())
        assertNull(cellFileDao.getById("direct"))
        assertNull(cellFileDao.getById("nested"))
        assertNull(cellFileDao.getById("not_downloaded"))
        // Only exact prefixes: "folder2" is not inside "folder".
        assertNotNull(cellFileDao.getById("sibling"))
        assertNotNull(cellFileDao.getById("sibling_before"))
        assertNotNull(cellFileDao.getById("sibling_after"))
        assertNotNull(cellFileDao.getById("outside"))
    }

    @Test
    fun givenAFolderNameWithLikeWildcards_whenDeletedUnderFolder_thenTheyAreMatchedLiterally() = runTest(dispatcher) {
        cellFileDao.upsert(fileAt("inside", "conv/a_b%/file.pdf", localPath = null))
        cellFileDao.upsert(fileAt("lookalike", "conv/aXbY/file.pdf", localPath = null))

        cellFileDao.deleteUnderFolder("conv/a_b%", "NOT_FOUND")

        assertNull(cellFileDao.getById("inside"))
        assertNotNull(cellFileDao.getById("lookalike"))
    }

    @Test
    fun givenAnAttachmentUnderAFolder_whenDeletedUnderFolder_thenItIsKeptAsNotFoundWithoutLocalCopy() = runTest(dispatcher) {
        insertMessageWithAttachment(assetId = "attached", assetPath = "conv/folder/attached.pdf")

        val localPaths = cellFileDao.deleteUnderFolder("conv/folder", "NOT_FOUND")

        assertEquals(listOf("/tmp/attached"), localPaths.map { it.localPath })
        val attachment = database.messageAttachments.getAttachment("attached")
        assertEquals("NOT_FOUND", attachment.assetTransferStatus)
        assertNull(attachment.localPath)
    }

    private fun fileAt(uuid: String, remotePath: String, localPath: String?) = CellFileEntity(
        uuid = uuid,
        conversationId = "conv@domain",
        name = remotePath.substringAfterLast('/'),
        owner = null,
        localPath = localPath,
        size = 1024,
        downloadedAt = 1000L,
        isOffline = true,
        remotePath = remotePath,
    )

    private suspend fun insertMessageWithAttachment(assetId: String, assetPath: String) {
        val senderId = UserIDEntity("sender", "domain")
        val conversationId = ConversationIDEntity("conversation", "domain")
        database.userDAO.upsertUser(newUserEntity(qualifiedID = senderId))
        database.conversationDAO.insertConversation(newConversationEntity(id = conversationId))
        database.messageDAO.insertOrIgnoreMessage(
            newRegularMessageEntity(
                id = "message-$assetId",
                conversationId = conversationId,
                senderUserId = senderId,
                content = MessageEntityContent.Multipart(
                    messageBody = "multipart",
                    attachments = listOf(
                        MessageAttachmentEntity(
                            assetId = assetId,
                            cellAsset = true,
                            mimeType = "application/pdf",
                            assetPath = assetPath,
                            assetSize = 128,
                            localPath = "/tmp/$assetId",
                            previewUrl = null,
                            assetWidth = null,
                            assetHeight = null,
                            assetDuration = null,
                            assetTransferStatus = "SAVED_INTERNALLY",
                            contentUrl = null,
                            contentHash = null,
                            assetIndex = 0,
                            contentExpiresAt = null,
                            isEditSupported = false,
                        )
                    ),
                )
            )
        )
    }

    private fun offlineFile(remotePath: String? = null) = CellFileEntity(
        uuid = "file-id",
        conversationId = "conv@domain",
        name = "file.pdf",
        owner = "owner",
        mimeType = "application/pdf",
        localPath = "/data/file.pdf",
        size = 1024,
        downloadedAt = 1000L,
        isOffline = true,
        modifiedAt = 500L,
        remotePath = remotePath,
    )

}
