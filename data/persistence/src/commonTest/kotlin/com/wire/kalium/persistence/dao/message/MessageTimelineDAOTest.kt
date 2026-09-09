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

import app.cash.sqldelight.Query
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.client.DeviceTypeEntity
import com.wire.kalium.persistence.dao.client.InsertClientParam
import com.wire.kalium.persistence.dao.message.attachment.MessageAttachmentEntity
import com.wire.kalium.persistence.dao.receipt.ReceiptTypeEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newSystemMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MessageTimelineDAOTest : BaseDatabaseTest() {
    private lateinit var db: UserDatabaseBuilder
    private lateinit var driver: RecordingDriver
    private val self = newUserEntity("timeline-self")
    private val other = newUserEntity("timeline-other")
    private val conversation = newConversationEntity("timeline")
    private val otherConversation = newConversationEntity("another-timeline")
    private val sessions = mutableListOf<MessageTimelineSession>()

    @BeforeTest
    fun setUp() {
        val original = createDatabase(self.id, encryptedDBSecret, true, dbInvalidationControlEnabled = true)
        driver = RecordingDriver(original.sqlDriver)
        db = UserDatabaseBuilder(self.id, driver, dispatcher, platformDBData(self.id), false, original.dbInvalidationController)
    }

    @AfterTest
    fun tearDown() {
        sessions.forEach { it.close() }
        driver.close()
    }

    private suspend fun seed(messages: List<MessageEntity> = emptyList()) {
        db.userDAO.upsertUsers(listOf(self, other))
        db.conversationDAO.insertConversations(listOf(conversation, otherConversation))
        db.messageDAO.insertOrIgnoreMessages(messages, withUnreadEvents = false)
    }

    private fun message(index: Int, date: Instant = Instant.fromEpochMilliseconds(1_000L + index / 70)) = newRegularMessageEntity(
        id = index.toString().padStart(4, '0'), conversationId = conversation.id, senderUserId = other.id,
        status = MessageEntity.Status.SENT, date = date,
    )

    private fun open(includeDeleted: Boolean = true, conversationId: QualifiedIDEntity = conversation.id) =
        db.messageTimelineDAO.open(conversationId, includeDeleted).also(sessions::add)

    private suspend fun MessageTimelineSession.page(
        request: MessageTimelineRequestEntity = MessageTimelineRequestEntity.Latest,
        limit: Int = 20,
    ) = assertIs<MessageTimelineLoadResultEntity.Success>(load(request, limit)).page

    @Test
    fun emptyAndBoundedLoads() = runTest(dispatcher) {
        seed()
        val empty = open().page()
        assertTrue(empty.rows.isEmpty())
        assertNull(empty.olderCursor)
        assertNull(empty.newerCursor)
        assertFailsWith<IllegalArgumentException> { open().load(MessageTimelineRequestEntity.Latest, 0) }
        assertFailsWith<IllegalArgumentException> { open().load(MessageTimelineRequestEntity.Latest, -1) }
        db.messageDAO.insertOrIgnoreMessages((0..136).map(::message), withUnreadEvents = false)
        for (size in listOf(1, 20, 60, 100, Int.MAX_VALUE)) {
            val page = open().page(limit = size)
            assertEquals(size.coerceAtMost(100), page.rows.size)
            assertEquals(page.rows.last().id, page.olderCursor?.messageId)
            assertNull(page.newerCursor)
        }
    }

    @Test
    fun bothDirectionsTraverseTimestampTiesExactlyOnce() = runTest(dispatcher) {
        val messages = (0..136).map(::message)
        seed(messages)
        val expected = messages.sortedWith(compareByDescending<MessageEntity> { it.date }.thenByDescending { it.id }).map { it.id }
        for (size in listOf(1, 20, 60, 100)) {
            val timeline = open()
            var page = timeline.page(limit = size)
            val descending = page.rows.map { it.id }.toMutableList()
            while (page.olderCursor != null) {
                page = timeline.page(MessageTimelineRequestEntity.Older(requireNotNull(page.olderCursor)), size)
                descending.addAll(page.rows.map { it.id })
            }
            assertEquals(expected, descending)
            assertNull(page.olderCursor)
            val ascending = page.rows.map { it.id }.toMutableList()
            while (page.newerCursor != null) {
                page = timeline.page(MessageTimelineRequestEntity.Newer(requireNotNull(page.newerCursor)), size)
                ascending.addAll(0, page.rows.map { it.id })
            }
            assertEquals(expected, ascending)
        }
    }

    @Test
    fun idsUseSqliteBinaryOrderAndDatesRetainDatabasePrecision() = runTest(dispatcher) {
        val date = Instant.parse("2026-09-05T12:00:00.123456789Z")
        seed(listOf("A", "z", "\uE000", "😀").mapIndexed { index, id -> message(index, date).copy(id = id) })
        val timeline = open()
        var page = timeline.page(limit = 1)
        val ids = mutableListOf(page.rows.single().id)
        assertEquals(Instant.fromEpochMilliseconds(date.toEpochMilliseconds()), page.rows.single().creation_date)
        while (page.olderCursor != null) {
            page = timeline.page(MessageTimelineRequestEntity.Older(requireNotNull(page.olderCursor)), 1)
            ids += page.rows.single().id
        }
        assertEquals(listOf("😀", "\uE000", "z", "A"), ids)
    }

    @Test
    fun aroundLoadsFillTheirBudgetAtBothEndsAndIncludeTheAnchorOnce() = runTest(dispatcher) {
        seed((0..136).map(::message))
        for (size in listOf(1, 20, 60, 100)) {
            for (anchor in listOf(0, 1, 70, 135, 136)) {
                val page = open().page(MessageTimelineRequestEntity.AroundMessage(message(anchor).id), size)
                assertEquals(size, page.rows.size)
                assertEquals(1, page.rows.count { it.id == message(anchor).id })
                assertEquals(message(anchor).id, page.anchorMessageId)
                assertEquals(page.rows.size, page.rows.map { it.id }.distinct().size)
            }
        }
        assertIs<MessageTimelineLoadResultEntity.AnchorNotFound>(open().load(MessageTimelineRequestEntity.AroundMessage("missing"), 20))
    }

    @Test
    fun firstUnreadIsStrictlyAfterTheReadDateAndBreaksTiesById() = runTest(dispatcher) {
        seed((0..136).map(::message))
        val page = open().page(MessageTimelineRequestEntity.AtReadBoundary(Instant.fromEpochMilliseconds(1_000)), 20)
        assertEquals("0070", page.anchorMessageId)
        assertEquals(20, page.rows.size)
        val latest = open().page(MessageTimelineRequestEntity.AtReadBoundary(Instant.fromEpochMilliseconds(2_000)))
        assertEquals("0136", latest.rows.first().id)
        assertNull(latest.newerCursor)
    }

    @Test
    fun cursorsRejectAnotherConversationPolicyAccountAndGeneration() = runTest(dispatcher) {
        seed((0..40).map(::message))
        val timeline = open()
        val cursor = assertNotNull(timeline.page().olderCursor)
        val request = MessageTimelineRequestEntity.Older(cursor)
        assertIs<MessageTimelineLoadResultEntity.Success>(timeline.load(request, 20))
        assertIs<MessageTimelineLoadResultEntity.InvalidCursor>(open(conversationId = otherConversation.id).load(request, 20))
        assertIs<MessageTimelineLoadResultEntity.InvalidCursor>(open(includeDeleted = false).load(request, 20))
        assertIs<MessageTimelineLoadResultEntity.InvalidCursor>(open().load(request, 20))
        val otherAccount = MessageTimelineDAO(db.database.messageTimelineQueries, driver, db.readDispatcher).open(conversation.id)
        otherAccount.use {
            assertIs<MessageTimelineLoadResultEntity.InvalidCursor>(it.load(MessageTimelineRequestEntity.AroundMessage(cursor.messageId, cursor), 20))
        }
        assertIs<MessageTimelineLoadResultEntity.InvalidCursor>(open().load(MessageTimelineRequestEntity.AroundMessage("different", cursor), 20))
    }

    @Test
    fun refreshResolvesMovedAnchorsAndFallsBackToTheirSavedTupleAfterDeletion() = runTest(dispatcher) {
        seed((0..40).map { message(it, Instant.fromEpochMilliseconds(1_000L + it)) })
        val timeline = open()
        val cursor = timeline.page(MessageTimelineRequestEntity.AroundMessage("0020"), 20).cursors.getValue("0020")
        db.messageDAO.promoteMessageToSentUpdatingServerTime(conversation.id, "0020", Instant.fromEpochMilliseconds(3_000), 0)
        assertTrue(timeline.invalidated.value)
        assertIs<MessageTimelineLoadResultEntity.Invalidated>(timeline.load(MessageTimelineRequestEntity.Older(cursor), 20))
        val moved = open().page(MessageTimelineRequestEntity.AroundMessage("0020", cursor), 20)
        assertEquals("0020", moved.rows.first().id)
        assertEquals(Instant.fromEpochMilliseconds(3_000), moved.rows.first().creation_date)
        db.messageDAO.deleteMessage("0020", conversation.id)
        val fallback = open().page(MessageTimelineRequestEntity.AroundMessage("0020", cursor), 20)
        assertEquals(20, fallback.rows.size)
        assertFalse(fallback.rows.any { it.id == "0020" })
        assertEquals("0019", fallback.anchorMessageId)
        assertIs<MessageTimelineLoadResultEntity.AnchorNotFound>(open().load(MessageTimelineRequestEntity.AroundMessage("0020"), 20))
    }

    @Test
    fun eligibilityIsAppliedBeforeLimitAndKeepsDeletedRegularMarkers() = runTest(dispatcher) {
        val incomplete = MessageEntityContent.Asset(
            assetSizeInBytes = 100, assetMimeType = "image/png", assetOtrKey = byteArrayOf(), assetSha256Key = byteArrayOf(),
            assetId = "", assetEncryptionAlgorithm = null, assetWidth = 10, assetHeight = 10,
        )
        val eligible = (0..24).map(::message) + listOf(
            message(200).copy(visibility = MessageEntity.Visibility.DELETED),
            message(201).copy(content = incomplete, senderUserId = self.id),
            message(202).copy(content = incomplete.copy(assetWidth = 0)),
            message(203).copy(content = incomplete.copy(assetMimeType = "application/pdf")),
            message(204).copy(content = incomplete.copy(assetId = "remote", assetOtrKey = byteArrayOf(1), assetSha256Key = byteArrayOf(2))),
            newSystemMessageEntity("self-admin", MessageEntityContent.MemberChange(listOf(self.id), MessageEntity.MemberChangeType.USER_PROMOTED_TO_ADMIN),
                conversation.id, other.id, date = Instant.fromEpochMilliseconds(3_000)),
        )
        val excluded = (300..449).map { message(it).copy(visibility = MessageEntity.Visibility.HIDDEN) } + listOf(
            message(500).copy(content = incomplete),
            newSystemMessageEntity("deleted-system", MessageEntityContent.HistoryLost, conversation.id, other.id, visibility = MessageEntity.Visibility.DELETED),
            newSystemMessageEntity("hidden-system", MessageEntityContent.HistoryLost, conversation.id, other.id, visibility = MessageEntity.Visibility.HIDDEN),
            newSystemMessageEntity("other-admin", MessageEntityContent.MemberChange(listOf(other.id), MessageEntity.MemberChangeType.USER_PROMOTED_TO_ADMIN),
                conversation.id, other.id),
        )
        seed(eligible + excluded)
        val timeline = open()
        val first = timeline.page()
        assertEquals(20, first.rows.size)
        val last = timeline.page(MessageTimelineRequestEntity.Older(requireNotNull(first.olderCursor)))
        assertEquals(eligible.map { it.id }.toSet(), (first.rows + last.rows).map { it.id }.toSet())
        assertNull(last.olderCursor)
        assertFalse(open(includeDeleted = false).page(limit = 100).rows.any { it.visibility == MessageEntity.Visibility.DELETED })
        val aroundHidden = open().page(MessageTimelineRequestEntity.AroundMessage("0300"))
        assertEquals(20, aroundHidden.rows.size)
        assertFalse(aroundHidden.rows.any { it.visibility == MessageEntity.Visibility.HIDDEN })
    }

    @Test
    fun pageAssemblyScopesCollectionsToTheConversationAndBatchesMultipart() = runTest(dispatcher) {
        val attachment = MessageAttachmentEntity(
            "cell", cellAsset = true, mimeType = "image/png", assetPath = "photo.png", assetSize = 100,
            assetWidth = 10, assetHeight = 10, assetDuration = null, assetTransferStatus = "NOT_DOWNLOADED", isEditSupported = true,
        )
        val multipart = MessageEntityContent.Multipart("body with \"quotes\" and \n newlines", attachments = listOf(attachment))
        seed((0..24).map { message(it).copy(content = multipart.copy(attachments = listOf(attachment.copy(assetId = "cell-$it")))) } +
            message(24).copy(conversationId = otherConversation.id, content = MessageEntityContent.Text("another conversation")))
        db.receiptDAO.insertReceipts(other.id, otherConversation.id, Instant.fromEpochMilliseconds(4_000), ReceiptTypeEntity.READ, listOf("0024"))
        db.database.reactionsQueries.insertReaction("0024", conversation.id, self.id, "\"\\emoji", "date")
        val timeline = open()
        driver.queries.clear()
        val page = timeline.page()
        assertEquals(20, page.rows.size)
        assertEquals(20, page.attachments.size)
        assertEquals(1, driver.queries.count { it.contains("FROM MessageAttachments ma") })
        assertEquals(1, driver.queries.count { it.contains("FROM User u") })
        assertTrue(driver.queries.none { it.contains("MessageDetailsView") || it.contains("UserDetails") || it.contains("OFFSET") })
        assertNull(page.readCounts["0024"])
        assertEquals("\"\\emoji", page.reactions.getValue("0024").single().emoji)
        assertEquals(setOf(other.id), page.users.keys)
        assertFalse(page.attachments.containsKey("0004")) // the extra key has no hydrated content
    }

    @Test
    fun changesToEarlierPagesInvalidateAfterALaterPageWasLoadedAndBatchingIsRespected() = runTest(dispatcher) {
        seed((0..40).map(::message))
        val mutations: List<suspend () -> Unit> = listOf(
            { db.database.reactionsQueries.insertReaction("0040", conversation.id, self.id, "😀", "date") },
            { db.receiptDAO.insertReceipts(other.id, conversation.id, Instant.fromEpochMilliseconds(5_000), ReceiptTypeEntity.READ, listOf("0040")) },
            { db.database.usersQueries.updateUserDisplayName("renamed", other.id) },
            { db.database.messagesQueries.updateMessageTextContent("edited", "0040", conversation.id) },
        )
        for (mutate in mutations) {
            val timeline = open()
            val first = timeline.page()
            timeline.page(MessageTimelineRequestEntity.Older(requireNotNull(first.olderCursor)))
            db.dbInvalidationController.runMuted {
                mutate()
                assertFalse(timeline.invalidated.value)
            }
            assertTrue(timeline.invalidated.value)
        }
    }

    @Test
    fun closingOrInvalidatingDuringAssemblyDiscardsTheLoadAndRemovesListeners() = runTest(dispatcher) {
        seed((0..40).map(::message))
        val timeline = open()
        assertEquals(1, driver.listeners.size)
        driver.beforeQuery = { sql -> if (sql.contains("FROM Message m")) timeline.close() }
        assertIs<MessageTimelineLoadResultEntity.Invalidated>(timeline.load(MessageTimelineRequestEntity.Latest, 20))
        assertTrue(driver.listeners.isEmpty())
        timeline.close()
        assertTrue(driver.listeners.isEmpty())
    }

    @Test
    fun collectionAndClientWritesRefreshRetainedPages() = runTest(dispatcher) {
        val attachment = MessageAttachmentEntity(
            "retained-cell", cellAsset = true, mimeType = "image/png", assetPath = "image", assetSize = 100,
            assetWidth = 10, assetHeight = 10, assetDuration = null, assetTransferStatus = "NOT_DOWNLOADED", isEditSupported = false,
        )
        seed((0..40).map(::message) + listOf(
            message(41).copy(content = MessageEntityContent.Multipart("body", attachments = listOf(attachment))),
            message(42).copy(content = MessageEntityContent.Composite(MessageEntityContent.Text("button"), listOf(ButtonEntity("text", "button", false)))),
            message(43).copy(content = MessageEntityContent.Text("link", linkPreview = listOf(
                MessageEntity.LinkPreview("url", 0, "url", "title", "summary")
            ))),
        ))
        val mutations: List<suspend () -> Unit> = listOf(
            { db.database.messageAttachmentsQueries.setPreviewUrl("new-preview", "retained-cell") },
            { db.database.cellFilesQueries.setTransferStatus("NOT_FOUND", "retained-cell") },
            { db.database.buttonContentQueries.markSelected(conversation.id, "0042", "button").await() },
            { db.messageDAO.updateLinkPreviewImageLocalPath(conversation.id, "0043", 0, "image-path") },
            { db.database.messagesQueries.insertMessageMention("0043", conversation.id, 0, 2, self.id) },
            { db.messageDAO.insertFailedRecipientDelivery("0043", conversation.id, listOf(self.id), RecipientFailureTypeEntity.MESSAGE_DELIVERY_FAILED) },
            { db.clientDAO.insertClient(InsertClientParam(other.id, "legal-hold", DeviceTypeEntity.LegalHold, null, null, null, null, null, null, false, false)) },
        )
        for (mutate in mutations) {
            val timeline = open()
            val first = timeline.page()
            timeline.page(MessageTimelineRequestEntity.Older(requireNotNull(first.olderCursor)))
            mutate()
            assertTrue(timeline.invalidated.value)
        }
        val refreshed = open().page()
        assertEquals("new-preview", refreshed.attachments.getValue("0041").single().previewUrl)
        assertEquals("NOT_FOUND", refreshed.attachments.getValue("0041").single().assetTransferStatus)
        assertTrue(refreshed.buttons.getValue("0042").single().is_selected)
        assertTrue(refreshed.users.getValue(other.id).legal_hold)
    }

    @Test
    fun largeMemberListsUseBoundedUserBindingsAndKeepMissingMembers() = runTest(dispatcher) {
        val members = (0..1_100).map { newUserEntity("member-$it") }
        val missing = QualifiedIDEntity("missing", "domain")
        seed(listOf(newSystemMessageEntity(
            "many-members", MessageEntityContent.MemberChange(members.map { it.id } + missing, MessageEntity.MemberChangeType.ADDED),
            conversation.id, other.id,
        )))
        db.userDAO.upsertUsers(members)
        driver.queries.clear()
        val page = open().page()
        assertEquals(1_102, page.users.size)
        val event = assertIs<MessageEntityContent.MemberChange>(page.systemContents.getValue("many-members"))
        assertEquals(1_102, event.memberUserIdList.size)
        assertEquals(missing, event.memberUserIdList.last())
        val userQueries = driver.queries.filter { it.contains("FROM User u") }
        assertEquals(3, userQueries.size)
        assertTrue(userQueries.all { query -> query.count { it == '?' } <= 500 })
    }

    @Test
    fun callerCancellationPropagatesAndDoesNotCloseTheReusableSession() = runTest(dispatcher) {
        seed((0..40).map(::message))
        val timeline = open()
        val load = async { timeline.load(MessageTimelineRequestEntity.Latest, 20) }
        driver.beforeQuery = { load.cancel() }
        assertFailsWith<CancellationException> { load.await() }
        driver.beforeQuery = null
        assertFalse(timeline.invalidated.value)
        assertEquals(20, timeline.page().rows.size)
    }

    private class RecordingDriver(private val delegate: SqlDriver) : SqlDriver by delegate {
        val queries = mutableListOf<String>()
        val listeners = mutableSetOf<Query.Listener>()
        var beforeQuery: ((String) -> Unit)? = null

        override fun <R> executeQuery(
            identifier: Int?, sql: String, mapper: (SqlCursor) -> QueryResult<R>, parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<R> {
            queries += sql
            beforeQuery?.invoke(sql)
            return delegate.executeQuery(identifier, sql, mapper, parameters, binders)
        }

        override fun addListener(vararg queryKeys: String, listener: Query.Listener) {
            listeners += listener
            delegate.addListener(*queryKeys, listener = listener)
        }

        override fun removeListener(vararg queryKeys: String, listener: Query.Listener) {
            listeners -= listener
            delegate.removeListener(*queryKeys, listener = listener)
        }
    }
}
