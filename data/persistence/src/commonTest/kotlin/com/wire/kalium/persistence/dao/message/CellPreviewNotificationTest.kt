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
package com.wire.kalium.persistence.dao.message

import app.cash.turbine.test
import app.cash.sqldelight.async.coroutines.awaitAsOne
import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.cellfile.CellFileEntity
import com.wire.kalium.persistence.dao.message.attachment.MessageAttachmentEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CellPreviewNotificationTest : BaseDatabaseTest() {
    private val self = newUserEntity("cell-preview-self")
    private val first = newConversationEntity("cell-preview-first")
    private val second = newConversationEntity("cell-preview-second")
    private lateinit var db: UserDatabaseBuilder

    @BeforeTest
    fun setUp() {
        deleteDatabase(self.id)
        db = createDatabase(self.id, encryptedDBSecret, true)
    }

    @AfterTest
    fun tearDown() {
        db.sqlDriver.close()
        deleteDatabase(self.id)
    }

    private suspend fun seed() {
        db.userDAO.upsertUser(self)
        db.conversationDAO.insertConversations(listOf(first, second))
        val attachment = MessageAttachmentEntity("cell", cellAsset = true, mimeType = "image/png", assetPath = "remote/path",
            assetSize = 100, assetWidth = 10, assetHeight = 10, assetDuration = null,
            assetTransferStatus = "SAVED_INTERNALLY", localPath = "offline/path", isEditSupported = false)
        db.messageDAO.insertOrIgnoreMessages(listOf(first, second).map {
            newRegularMessageEntity("message", conversationId = it.id, senderUserId = self.id,
                content = MessageEntityContent.Multipart("body", attachments = listOf(attachment)))
        })
    }

    @Test
    fun savingAndClearingPreviewRefreshesEveryPersistedConversationAndAttachments() = runTest(dispatcher) {
        seed()
        val observeFirst = db.messageDAO.observeMessagesByConversationAndVisibilityAfterDate(first.id, "1970-01-01T00:00:00Z")
        val observeSecond = db.messageDAO.observeMessagesByConversationAndVisibilityAfterDate(second.id, "1970-01-01T00:00:00Z")
        observeFirst.test {
            assertEquals(null, assertIs<MessageEntityContent.Multipart>(awaitItem().single().content).attachments.single().previewUrl)
            observeSecond.test {
                awaitItem()
                db.messageAttachments.observeAttachments().test {
                    awaitItem()
                    db.messageAttachments.setPreviewUrl("cell", "image-preview")
                    assertEquals(listOf("image-preview", "image-preview"), awaitItem().map { it.previewUrl })
                }
                assertEquals("image-preview", assertIs<MessageEntityContent.Multipart>(awaitItem().single().content).attachments.single().previewUrl)
            }
            assertEquals("image-preview", assertIs<MessageEntityContent.Multipart>(awaitItem().single().content).attachments.single().previewUrl)
            db.messageAttachments.setPreviewUrl("cell", null)
            val attachment = assertIs<MessageEntityContent.Multipart>(awaitItem().single().content).attachments.single()
            assertEquals(null, attachment.previewUrl)
            assertEquals("offline/path", attachment.localPath)
            assertEquals("remote/path", attachment.assetPath)
            assertEquals("SAVED_INTERNALLY", attachment.assetTransferStatus)
        }
    }

    @Test
    fun previewUpdateInvalidatesNativeTimelineAndReloadsHydratedAttachment() = runTest(dispatcher) {
        seed()
        val timeline = db.messageTimelineDAO.open(first.id, true)
        try {
            assertIs<MessageTimelineLoadResultEntity.Success>(timeline.load(MessageTimelineRequestEntity.Latest, 20))
            assertFalse(timeline.invalidated.value)
            db.messageAttachments.setPreviewUrl("cell", "native-preview")
            assertTrue(timeline.invalidated.value)
        } finally { timeline.close() }
        val refreshed = db.messageTimelineDAO.open(first.id, true)
        try {
            val page = assertIs<MessageTimelineLoadResultEntity.Success>(refreshed.load(MessageTimelineRequestEntity.Latest, 20)).page
            assertEquals("native-preview", page.attachments.getValue("message").single().previewUrl)
        } finally { refreshed.close() }
    }

    @Test
    fun unlinkedOfflineFilePreviewStillUpdatesWithoutAMessageRelation() = runTest(dispatcher) {
        db.cellFileDao.upsert(CellFileEntity("offline", "conversation", "file", null, "image/png", "local", 100, 1, true,
            remotePath = "remote"))
        db.messageAttachments.setPreviewUrl("offline", "preview")
        assertEquals("preview", db.database.cellFilesQueries.selectById("offline").awaitAsOne().previewUrl)
        assertEquals("remote", db.cellFileDao.getById("offline")?.remotePath)
    }
}
