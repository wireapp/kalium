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
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.db.SqlDriver
import com.wire.kalium.persistence.MessageTimelineQueries
import com.wire.kalium.persistence.TimelineRows
import com.wire.kalium.persistence.adapter.QualifiedIDListAdapter
import com.wire.kalium.persistence.adapter.StringListAdapter
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.db.ReadDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Instant

class MessageTimelineDAO internal constructor(
    private val queries: MessageTimelineQueries,
    private val driver: SqlDriver,
    private val readDispatcher: ReadDispatcher,
) {
    // Shared by sessions of this database, never by a different account/database instance.
    private val cursorScope = Any()

    fun open(
        conversationId: ConversationIDEntity,
        includeDeleted: Boolean = true,
        onInvalidated: () -> Unit = {},
    ): MessageTimelineSession = MessageTimelineSession(
        queries,
        driver,
        readDispatcher,
        cursorScope,
        conversationId,
        includeDeleted,
        onInvalidated
    )
}

/** One invalidatable read generation. Register before loading and close when the consumer leaves. */
@Suppress("LongParameterList", "TooManyFunctions", "SpreadOperator")
class MessageTimelineSession internal constructor(
    private val queries: MessageTimelineQueries,
    private val driver: SqlDriver,
    private val readDispatcher: ReadDispatcher,
    private val cursorScope: Any,
    private val conversationId: ConversationIDEntity,
    private val includeDeleted: Boolean,
    private val onInvalidated: () -> Unit,
) : AutoCloseable, Query.Listener {
    private val generation = Any()
    private val mutableInvalidated = MutableStateFlow(false)
    val invalidated: StateFlow<Boolean> = mutableInvalidated.asStateFlow()
    private val visibilities = if (includeDeleted) {
        listOf(MessageEntity.Visibility.VISIBLE, MessageEntity.Visibility.DELETED)
    } else {
        listOf(MessageEntity.Visibility.VISIBLE)
    }

    // Keep these for the whole generation, including pages retained before the last load.
    // Custom keys respect DbInvalidationController batching; table keys also cover child-only writes.
    private val dependencies = arrayOf(
        "message_list_$conversationId", "message_list", "Message", "MessageTextContent", "MessageAssetContent",
        "MessageRestrictedAssetContent", "MessageUnknownContent", "MessageSystemContent", "MessageConversationLocationContent",
        "MessageMention", "MessageLinkPreview", "MessageRecipientFailure", "ButtonContent", "PollContent", "PollVoteContent",
        "Reaction", "Receipt",
        "MessageAttachments", "CellFile", "User", "Client", "SelfUser",
    )

    init {
        driver.addListener(*dependencies, listener = this)
    }

    override fun queryResultsChanged() = close()

    override fun close() {
        if (mutableInvalidated.compareAndSet(expect = false, update = true)) {
            driver.removeListener(*dependencies, listener = this)
            onInvalidated()
        }
    }

    /** Limits are positive and capped at 100. Cancellation from the caller always propagates. */
    @Suppress("ReturnCount")
    suspend fun load(request: MessageTimelineRequestEntity, limit: Int): MessageTimelineLoadResultEntity {
        require(limit > 0) { "The message limit must be positive" }
        if (invalidated.value) return MessageTimelineLoadResultEntity.Invalidated
        if (!accepts(request)) return MessageTimelineLoadResultEntity.InvalidCursor
        return try {
            coroutineScope {
                val loadScope = this
                val invalidation = launch(start = CoroutineStart.UNDISPATCHED) {
                    invalidated.first { it }
                    loadScope.cancel(TimelineInvalidated())
                }
                try {
                    val result = withContext(readDispatcher.value) {
                        queries.transactionWithResult { loadPage(request, limit.coerceAtMost(MAX_PAGE_SIZE)) }
                    }
                    if (invalidated.value) MessageTimelineLoadResultEntity.Invalidated else result
                } finally {
                    invalidation.cancel()
                }
            }
        } catch (cancelled: TimelineInvalidated) {
            currentCoroutineContext().ensureActive()
            MessageTimelineLoadResultEntity.Invalidated
        }
    }

    private fun accepts(request: MessageTimelineRequestEntity): Boolean {
        val cursor = when (request) {
            is MessageTimelineRequestEntity.Older -> request.cursor
            is MessageTimelineRequestEntity.Newer -> request.cursor
            is MessageTimelineRequestEntity.AroundMessage -> request.fallback
            else -> null
        } ?: return true
        val sameScope = cursor.scope === cursorScope && cursor.conversationId == conversationId && cursor.includeDeleted == includeDeleted
        return sameScope && if (request is MessageTimelineRequestEntity.AroundMessage) {
            request.messageId == cursor.messageId
        } else {
            cursor.generation === generation
        }
    }

    private suspend fun loadPage(request: MessageTimelineRequestEntity, limit: Int): MessageTimelineLoadResultEntity {
        val selection = when (request) {
            MessageTimelineRequestEntity.Latest -> latest(limit)
            is MessageTimelineRequestEntity.Older -> directional(request.cursor, limit, older = true)
            is MessageTimelineRequestEntity.Newer -> directional(request.cursor, limit, older = false)
            is MessageTimelineRequestEntity.AroundMessage -> {
                val anchor = queries.timelineAnchor(conversationId, request.messageId, ::key).awaitAsOneOrNull() ?: request.fallback
                if (anchor == null) return MessageTimelineLoadResultEntity.AnchorNotFound
                around(anchor, limit)
            }
            is MessageTimelineRequestEntity.AtReadBoundary -> {
                val anchor = queries.timelineFirstUnread(conversationId, visibilities, request.lastReadDate, ::key).awaitAsOneOrNull()
                if (anchor == null) latest(limit) else around(anchor, limit)
            }
        }
        return MessageTimelineLoadResultEntity.Success(assemble(selection))
    }

    private fun key(id: String, date: Instant) = MessageTimelineCursorEntity(
        id,
        date,
        conversationId,
        cursorScope,
        generation,
        includeDeleted
    )

    private suspend fun older(cursor: MessageTimelineCursorEntity, count: Int) = queries.timelineOlder(
        conversationId,
        visibilities,
        cursor.date,
        cursor.messageId,
        count.toLong(),
        ::key
    ).awaitAsList()

    private suspend fun newer(cursor: MessageTimelineCursorEntity, count: Int) = queries.timelineNewer(
        conversationId,
        visibilities,
        cursor.date,
        cursor.messageId,
        count.toLong(),
        ::key
    ).awaitAsList()

    private suspend fun latest(limit: Int): Selection {
        val keys = queries.timelineLatest(conversationId, visibilities, limit + 1L, ::key).awaitAsList()
        return Selection(keys.take(limit), hasOlder = keys.size > limit, hasNewer = false)
    }

    private suspend fun directional(cursor: MessageTimelineCursorEntity, limit: Int, older: Boolean): Selection {
        val scanned = if (older) older(cursor, limit + 1) else newer(cursor, limit + 1)
        // Newer queries are ascending. Retain the nearest keys before reversing for display.
        val keys = scanned.take(limit).let { if (older) it else it.asReversed() }
        return Selection(
            keys,
            hasOlder = if (older) scanned.size > limit else keys.lastOrNull()?.let { older(it, 1).isNotEmpty() } == true,
            hasNewer = if (older) keys.firstOrNull()?.let { newer(it, 1).isNotEmpty() } == true else scanned.size > limit,
        )
    }

    private suspend fun around(anchor: MessageTimelineCursorEntity, limit: Int): Selection {
        val eligible = queries.timelineEligibleAnchor(conversationId, anchor.messageId, visibilities, ::key).awaitAsOneOrNull()
        val remaining = limit - if (eligible != null) 1 else 0
        // At most two bounded key reads. The unused half can be filled from either edge without rescanning history.
        val newer = newer(anchor, remaining + 1)
        val older = older(anchor, remaining + 1)
        var newerCount = minOf(remaining / 2, newer.size)
        val olderCount = minOf(remaining - newerCount, older.size)
        newerCount = minOf(remaining - olderCount, newer.size)
        val keys = newer.take(newerCount).asReversed() + listOfNotNull(eligible) + older.take(olderCount)
        return Selection(
            keys,
            older.size > olderCount,
            newer.size > newerCount,
            eligible?.messageId ?: older.firstOrNull()?.takeIf { olderCount > 0 }?.messageId
                ?: newer.firstOrNull()?.takeIf { newerCount > 0 }?.messageId,
        )
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private suspend fun assemble(selection: Selection): MessageTimelinePageEntity {
        val ids = selection.keys.map { it.messageId }
        val cursors = selection.keys.associateBy { it.messageId }
        if (ids.isEmpty()) return MessageTimelinePageEntity(emptyList(), emptyMap(), null, null, null)
        val rowsById = queries.timelineRows(conversationId, ids).awaitAsList().associateBy { it.id }
        val rows = ids.map { rowsById.getValue(it) }
        val visible = rows.filter { it.visibility == MessageEntity.Visibility.VISIBLE }
        val textIds = visible.filter { it.content_type in TEXT_TYPES }.map { it.id }
        val regularIds = visible.filter { it.content_type in REGULAR_TYPES }.map { it.id }
        val compositeIds = visible.filter { it.content_type == MessageEntity.ContentType.COMPOSITE }.map { it.id }
        val multipartIds = visible.filter { it.content_type == MessageEntity.ContentType.MULTIPART }.map { it.id }
        val pollIds = visible.filter { it.content_type == MessageEntity.ContentType.POLL }.map { it.id }
        val polls = if (pollIds.isEmpty()) emptyMap() else polls(pollIds)
        val failures = if (regularIds.isEmpty()) emptyList() else queries.timelineFailures(conversationId, regularIds).awaitAsList()
        val systems = visible.mapNotNull { row -> row.systemContent()?.let { row.id to it } }.toMap()
        val userIds = buildSet {
            rows.forEach { row ->
                add(row.sender_user_id)
                row.quote_sender_id?.let { add(it) }
            }
            systems.values.forEach { content ->
                when (content) {
                    is MessageEntityContent.MemberChange -> addAll(content.memberUserIdList)
                    is MessageEntityContent.LegalHold -> addAll(content.memberUserIdList)
                    else -> Unit
                }
            }
            failures.forEach { addAll(it.recipient_failure_list) }
            polls.values.forEach { poll -> poll.votes.forEach { add(it.voterId) } }
        }
        val users = userIds.toList().chunked(USER_BIND_LIMIT).flatMap { queries.timelineUsers(it).awaitAsList() }
        return MessageTimelinePageEntity(
            rows = rows,
            cursors = cursors,
            olderCursor = selection.keys.lastOrNull().takeIf { selection.hasOlder },
            newerCursor = selection.keys.firstOrNull().takeIf { selection.hasNewer },
            anchorMessageId = selection.anchorMessageId,
            mentions = if (textIds.isEmpty()) emptyMap() else {
                queries.timelineMentions(conversationId, textIds).awaitAsList().groupBy { it.message_id }
            },
            previews = if (textIds.isEmpty()) emptyMap() else {
                queries.timelinePreviews(conversationId, textIds).awaitAsList().groupBy { it.message_id }
            },
            buttons = if (compositeIds.isEmpty()) emptyMap() else {
                queries.timelineButtons(conversationId, compositeIds).awaitAsList().groupBy { it.message_id }
            },
            polls = polls,
            reactions = if (regularIds.isEmpty()) emptyMap() else {
                queries.timelineReactions(conversationId, regularIds).awaitAsList().groupBy { it.message_id }
            },
            readCounts = queries.timelineReadCounts(conversationId, ids).awaitAsList().associate { it.message_id to it.read_count },
            failures = failures.groupBy { it.message_id },
            attachments = if (multipartIds.isEmpty()) emptyMap() else {
                queries.timelineAttachments(conversationId, multipartIds).awaitAsList().groupBy { it.message_id }
            },
            systemContents = systems,
            users = users.associateBy { it.qualified_id },
        )
    }

    private suspend fun polls(ids: List<String>): Map<String, MessageEntityContent.Poll> {
        val votes = queries.timelinePollVotes(conversationId, ids).awaitAsList().groupBy { it.poll_message_id }
        return queries.timelinePolls(conversationId, ids).awaitAsList().associate { poll ->
            poll.message_id to MessageEntityContent.Poll(
                question = poll.question,
                options = PollContentJson.decodeOptions(poll.options_json),
                allowMultipleAnswers = poll.allow_multiple_answers,
                votes = votes[poll.message_id].orEmpty().map {
                    PollVoteEntity(it.voter_user_id, PollContentJson.decodeOptionIds(it.selected_option_ids_json), it.creation_date)
                }
            )
        }
    }

    private data class Selection(
        val keys: List<MessageTimelineCursorEntity>,
        val hasOlder: Boolean,
        val hasNewer: Boolean,
        val anchorMessageId: String? = null,
    )

    private class TimelineInvalidated : CancellationException("The message timeline generation changed")

    companion object {
        const val MAX_PAGE_SIZE = 100
        private const val USER_BIND_LIMIT = 500
        private val TEXT_TYPES = setOf(
            MessageEntity.ContentType.TEXT,
            MessageEntity.ContentType.COMPOSITE,
            MessageEntity.ContentType.MULTIPART
        )
        private val REGULAR_TYPES = TEXT_TYPES + setOf(
            MessageEntity.ContentType.ASSET, MessageEntity.ContentType.KNOCK, MessageEntity.ContentType.RESTRICTED_ASSET,
            MessageEntity.ContentType.UNKNOWN, MessageEntity.ContentType.FAILED_DECRYPTION, MessageEntity.ContentType.LOCATION,
            MessageEntity.ContentType.POLL,
        )
    }
}

@Suppress("CyclomaticComplexMethod", "LongMethod", "DEPRECATION")
private fun TimelineRows.systemContent(): MessageEntityContent.System? = when (content_type) {
    MessageEntity.ContentType.MEMBER_CHANGE -> MessageEntityContent.MemberChange(
        QualifiedIDListAdapter.decode(requireNotNull(system_list)),
        MessageEntity.MemberChangeType.valueOf(requireNotNull(system_enum))
    )
    MessageEntity.ContentType.LEGAL_HOLD -> MessageEntityContent.LegalHold(
        QualifiedIDListAdapter.decode(requireNotNull(system_list)),
        MessageEntity.LegalHoldType.valueOf(requireNotNull(system_enum))
    )
    MessageEntity.ContentType.FEDERATION -> MessageEntityContent.Federation(
        StringListAdapter.decode(requireNotNull(system_list)),
        MessageEntity.FederationType.valueOf(requireNotNull(system_enum))
    )
    MessageEntity.ContentType.CONVERSATION_RENAMED -> MessageEntityContent.ConversationRenamed(system_text.orEmpty())
    MessageEntity.ContentType.REMOVED_FROM_TEAM -> MessageEntityContent.TeamMemberRemoved("")
    MessageEntity.ContentType.MISSED_CALL -> MessageEntityContent.MissedCall
    MessageEntity.ContentType.CRYPTO_SESSION_RESET -> MessageEntityContent.CryptoSessionReset
    MessageEntity.ContentType.NEW_CONVERSATION_RECEIPT_MODE -> MessageEntityContent.NewConversationReceiptMode(system_boolean ?: false)
    MessageEntity.ContentType.CONVERSATION_RECEIPT_MODE_CHANGED ->
        MessageEntityContent.ConversationReceiptModeChanged(system_boolean ?: false)
    MessageEntity.ContentType.NEW_CONVERSATION_APPS_ENABLED -> MessageEntityContent.NewConversationAppsEnabled
    MessageEntity.ContentType.CONVERSATION_APPS_ENABLED_CHANGED ->
        MessageEntityContent.ConversationAppsAccessChanged(system_boolean ?: false)
    MessageEntity.ContentType.CONVERSATION_MESSAGE_TIMER_CHANGED -> MessageEntityContent.ConversationMessageTimerChanged(system_integer)
    MessageEntity.ContentType.CONVERSATION_PROTOCOL_CHANGED -> MessageEntityContent.ConversationProtocolChanged(
        system_enum?.let(ConversationEntity.Protocol::valueOf) ?: ConversationEntity.Protocol.PROTEUS
    )
    MessageEntity.ContentType.HISTORY_LOST -> MessageEntityContent.HistoryLost
    MessageEntity.ContentType.HISTORY_LOST_PROTOCOL_CHANGED -> MessageEntityContent.HistoryLostProtocolChanged
    MessageEntity.ContentType.CONVERSATION_CREATED -> MessageEntityContent.ConversationCreated
    MessageEntity.ContentType.MLS_WRONG_EPOCH_WARNING -> MessageEntityContent.MLSWrongEpochWarning
    MessageEntity.ContentType.CONVERSATION_DEGRADED_MLS -> MessageEntityContent.ConversationDegradedMLS
    MessageEntity.ContentType.CONVERSATION_DEGRADED_PROTEUS -> MessageEntityContent.ConversationDegradedProteus
    MessageEntity.ContentType.CONVERSATION_VERIFIED_MLS -> MessageEntityContent.ConversationVerifiedMLS
    MessageEntity.ContentType.CONVERSATION_VERIFIED_PROTEUS -> MessageEntityContent.ConversationVerifiedProteus
    MessageEntity.ContentType.CONVERSATION_PROTOCOL_CHANGED_DURING_CALL -> MessageEntityContent.ConversationProtocolChangedDuringACall
    MessageEntity.ContentType.CONVERSATION_STARTED_UNVERIFIED_WARNING -> MessageEntityContent.ConversationStartedUnverifiedWarning
    MessageEntity.ContentType.CONVERSATION_WITH_CELL -> MessageEntityContent.NewConversationWithCellMessage
    MessageEntity.ContentType.CONVERSATION_WITH_CELL_SELF_DELETE_DISABLED ->
        MessageEntityContent.NewConversationWithCellSelfDeleteDisabledMessage
    MessageEntity.ContentType.CELL_EDITOR_ACCESS -> MessageEntityContent.CellEditorAccessMessage
    MessageEntity.ContentType.CELL_VIEWER_ACCESS -> MessageEntityContent.CellViewerAccessMessage
    else -> null
}
