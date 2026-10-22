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

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Instant

class AssetMessageContinuationTest : BaseDatabaseTest() {
    private lateinit var db: UserDatabaseBuilder
    private lateinit var driver: RecordingDriver
    private val self = newUserEntity("continuation-self")
    private val sender = newUserEntity("continuation-sender")
    private val stranger = newUserEntity("continuation-stranger")
    private val conversation = newConversationEntity("continuation").copy(
        lastReadDate = Instant.fromEpochMilliseconds(0), lastModifiedDate = Instant.fromEpochMilliseconds(0),
    )
    private val original = MessageEntityContent.Asset(
        assetSizeInBytes = 100_000, assetName = "video.mp4", assetMimeType = "video/mp4",
        assetOtrKey = ByteArray(32) { 1 }, assetSha256Key = ByteArray(32) { 2 }, assetId = "video-id",
        assetEncryptionAlgorithm = "AES_GCM", assetWidth = 1920, assetHeight = 1080,
    )
    private val preview = AssetPreviewEntity(
        mimeType = "image/jpeg", size = 1024, width = 512, height = 288,
        id = "preview-id", domain = "preview.domain", token = "preview-token",
        otrKey = ByteArray(32) { 3 }, sha256 = ByteArray(32) { 4 }, encryptionAlgorithm = "AES_GCM",
    )
    private fun message(content: MessageEntityContent.Asset = original) = newRegularMessageEntity(
        id = "video", conversationId = conversation.id, senderUserId = sender.id,
        senderClientId = "sender-client", date = Instant.fromEpochMilliseconds(1000), content = content,
    )

    @BeforeTest
    fun setUp() {
        val originalDB = createDatabase(self.id, encryptedDBSecret, true)
        driver = RecordingDriver(originalDB.sqlDriver)
        db = UserDatabaseBuilder(self.id, driver, dispatcher, platformDBData(self.id), false, originalDB.dbInvalidationController)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
        deleteDatabase(self.id)
    }

    private suspend fun seed() {
        db.userDAO.upsertUsers(listOf(self, sender, stranger))
        db.conversationDAO.insertConversation(conversation)
    }

    @Test
    fun receivedPreviewFollowUpReadsAssetOnceAndKeepsOriginalImmutable() = runTest(dispatcher) {
        seed()
        val initial = message()
        db.messageDAO.insertReceivedMessage(initial, updateConversationOrder = true)
        assertFalse(db.messageDAO.observeUnreadEvents().first().isEmpty())
        db.database.unreadEventsQueries.deleteUnreadEvent(initial.id, initial.conversationId)
        val unreadBefore = db.messageDAO.observeUnreadEvents().first()
        assertEquals(emptyMap(), unreadBefore)
        driver.queries.clear()
        db.messageDAO.insertReceivedMessage(initial.copy(content = original.copy(
            assetName = "replacement.mp4", assetSizeInBytes = 1,
            assetOtrKey = ByteArray(32) { 8 }, assetSha256Key = ByteArray(32) { 9 }, assetPreview = preview,
        )), updateConversationOrder = true)
        assertSingleNarrowLookup()
        val reloaded = db.messageDAO.getMessageById(initial.id, initial.conversationId)!!
        assertAssetEquals(original.copy(assetPreview = preview), reloaded.content)
        assertEquals(unreadBefore, db.messageDAO.observeUnreadEvents().first())

        val conversationBeforeReplay = db.conversationDAO.getConversationById(conversation.id)
        driver.queries.clear()
        db.messageDAO.insertReceivedMessage(initial.copy(
            date = Instant.fromEpochMilliseconds(9999), content = original.copy(assetPreview = preview),
        ), updateConversationOrder = true)
        assertSingleNarrowLookup()
        assertEquals(conversationBeforeReplay, db.conversationDAO.getConversationById(conversation.id))
        assertEquals(unreadBefore, db.messageDAO.observeUnreadEvents().first())
    }

    @Test
    fun originalOnlyContinuationRevealsMessageAndRetainsPreviewAlreadyReceived() = runTest(dispatcher) {
        seed()
        val initial = message(original.copy(assetOtrKey = byteArrayOf(), assetSha256Key = byteArrayOf(), assetPreview = preview))
            .copy(visibility = MessageEntity.Visibility.HIDDEN)
        db.messageDAO.insertReceivedMessage(initial, updateConversationOrder = true)
        driver.queries.clear()
        db.messageDAO.insertReceivedMessage(
            initial.copy(visibility = MessageEntity.Visibility.VISIBLE, content = original), updateConversationOrder = true,
        )
        assertSingleNarrowLookup()
        val reloaded = db.messageDAO.getMessageById(initial.id, initial.conversationId)!!
        assertEquals(MessageEntity.Visibility.VISIBLE, reloaded.visibility)
        assertAssetEquals(original.copy(assetPreview = preview), reloaded.content)
    }

    @Test
    fun missingPreviewAndOtherSendersCannotReplaceExistingPreviewOrOriginal() = runTest(dispatcher) {
        seed()
        val initial = message(original.copy(assetPreview = preview))
        db.messageDAO.insertReceivedMessage(initial, updateConversationOrder = true)
        val updates = listOf(
            initial.copy(content = original), // Includes a malformed wire preview mapped to null.
            initial.copy(senderClientId = "other-client", content = original.copy(assetPreview = preview.copy(id = "spoofed"))),
            initial.copy(senderUserId = stranger.id, content = original.copy(assetPreview = preview.copy(id = "spoofed"))),
            initial.copy(content = original.copy(assetId = "different-original", assetPreview = preview.copy(id = "spoofed"))),
        )
        updates.forEach {
            driver.queries.clear()
            db.messageDAO.insertOrIgnoreMessages(listOf(it))
            assertSingleNarrowLookup()
            val reloaded = db.messageDAO.getMessageById(initial.id, initial.conversationId)!!
            assertAssetEquals(initial.content as MessageEntityContent.Asset, reloaded.content)
            assertEquals(sender.id, reloaded.senderUserId)
            assertContentEquals(original.assetOtrKey, (reloaded.content as MessageEntityContent.Asset).assetOtrKey)
        }
    }

    private fun assertAssetEquals(expected: MessageEntityContent.Asset, actualContent: MessageEntityContent) {
        val actual = actualContent as MessageEntityContent.Asset
        assertContentEquals(expected.assetOtrKey, actual.assetOtrKey)
        assertContentEquals(expected.assetSha256Key, actual.assetSha256Key)
        assertEquals(expected.copy(assetOtrKey = actual.assetOtrKey, assetSha256Key = actual.assetSha256Key), actual)
    }

    private fun assertSingleNarrowLookup() {
        assertEquals(1, driver.queries.count { it.contains("FROM Message\nJOIN MessageAssetContent AS Asset") })
        assertFalse(driver.queries.any { it.contains("MessageDetailsView") })
    }

    private class RecordingDriver(private val delegate: SqlDriver) : SqlDriver by delegate {
        val queries = mutableListOf<String>()
        override fun <R> executeQuery(
            identifier: Int?, sql: String, mapper: (SqlCursor) -> QueryResult<R>, parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<R> {
            queries += sql
            return delegate.executeQuery(identifier, sql, mapper, parameters, binders)
        }
    }
}
