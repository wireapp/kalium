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

@file:OptIn(ExperimentalObjCRefinement::class)

package com.wire.kalium.share

import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.featureFlags.BuildFileRestrictionState
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.util.InternalKaliumApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * How the app stores a handed-off share: with its own use cases, as the composer would, under [messageId]. Each returns
 * null once the message is stored, whether it is sent yet or not, or why it stored nothing.
 */
@InternalKaliumApi
// The app uses these from Kotlin; the extension's Swift never does.
@HiddenFromObjC
public interface ShareOutboxSending {
    public companion object {
        public const val UNAVAILABLE: String = "UNAVAILABLE"
        public const val UPLOAD_FAILED: String = "UPLOAD_FAILED"
        public const val FAILED: String = "FAILED"
    }

    /** Returns once incremental sync has processed pending events and reached live, or failed, before checking import rules. */
    public suspend fun awaitSynced()
    public suspend fun file(conversationId: ConversationId, file: ShareOutboxFile, messageId: String): String?
    public suspend fun cells(
        conversationId: ConversationId,
        files: List<ShareOutboxFile>,
        text: String,
        messageId: String,
    ): String?
    public suspend fun text(conversationId: ConversationId, text: String, messageId: String): String?
}

/** A handed-off file. The app's storage takes [path] over: it moves the file. */
@InternalKaliumApi
@HiddenFromObjC
public data class ShareOutboxFile(
    public val path: String,
    public val name: String,
    public val mimeType: String,
    public val sizeInBytes: Long,
    public val width: Int?,
    public val height: Int?,
    public val durationMs: Long?,
)

/**
 * The app's side of an account's share outbox (ADR 13). Run [serve] only while the app owns the account, and
 * one server per account at a time: it claims what it imports.
 */
@InternalKaliumApi
@HiddenFromObjC
public class ShareOutboxServer(
    rootPath: String,
    private val selfUserId: UserId,
    private val database: UserDatabaseBuilder,
    private val restriction: BuildFileRestrictionState,
    private val sending: ShareOutboxSending,
) {
    private val outbox = ShareOutbox(rootPath, selfUserId)

    /**
     * Answers the extension now, then whenever [doorbell] rings, until cancelled. Requests for conversations are
     * answered on their own, so a share being imported never keeps a sheet waiting for its list.
     */
    public suspend fun serve(doorbell: Flow<Unit>): Unit = coroutineScope {
        guarded("sweep") { outbox.sweep() }
        launch { doorbell.onStart { emit(Unit) }.conflate().collect { answerConversations() } }
        doorbell.onStart { emit(Unit) }.conflate().collect { importShares() }
    }

    internal suspend fun answerConversations() {
        guarded("conversations") { outbox.answerIfAsked { database.sharePicker(selfUserId, restriction) } }
    }

    internal suspend fun importShares() {
        for (id in guarded("claim") { outbox.claim() }.orEmpty()) {
            // An import that fails unexpectedly would fail again, so it ends with what it stored.
            val result = guarded("import") { import(id) } ?: FAILED
            guarded("finish") { outbox.finish(id, result) }
        }
    }

    /** Stores the share's items in order, skipping those an interrupted import already stored. */
    @Suppress("ReturnCount")
    private suspend fun import(id: String): String {
        val request = outbox.request(id) ?: return UNREADABLE
        // Catching up can change the conversation or the account's file rules, so they are checked after it.
        sending.awaitSynced()
        val conversationId = ConversationId(request.conversationValue, request.conversationDomain)
        refusal(conversationId, request)?.let { return it }
        val isCells = database.conversationDAO.getConversationDetailsById(conversationId.toDao())?.wireCell != null
        if (isCells && request.files.isNotEmpty()) {
            if (stored(conversationId, request.textMessageId)) return ShareOutbox.STORED
            val files = request.files.mapIndexed { index, file ->
                ShareOutboxFile(
                    outbox.importCopy(id, index),
                    file.name,
                    file.mimeType,
                    file.sizeInBytes,
                    file.width,
                    file.height,
                    file.durationMs
                )
            }
            return sending.cells(conversationId, files, request.text, request.textMessageId) ?: ShareOutbox.STORED
        }
        request.files.forEachIndexed { index, file ->
            if (!stored(conversationId, file.messageId)) {
                val shared = ShareOutboxFile(
                    path = outbox.importCopy(id, index),
                    name = file.name,
                    mimeType = file.mimeType,
                    sizeInBytes = file.sizeInBytes,
                    width = file.width,
                    height = file.height,
                    durationMs = file.durationMs,
                )
                sending.file(conversationId, shared, file.messageId)?.let { return it }
            }
        }
        if (request.text.isNotBlank() && !stored(conversationId, request.textMessageId)) {
            sending.text(conversationId, request.text, request.textMessageId)?.let { return it }
        }
        return ShareOutbox.STORED
    }

    /** The extension's rules, checked again: the conversation or the account's file rules may have changed since. */
    @Suppress("ReturnCount")
    private suspend fun refusal(conversationId: ConversationId, request: OutboxRequest): String? {
        val conversation = database.conversationDAO.getConversationDetailsById(conversationId.toDao())
            ?: return ShareBlock.UNAVAILABLE.name
        database.shareBlock(conversation)?.let { return it.name }
        if (request.files.isEmpty()) return null
        val policy = database.sharePolicy(selfUserId, restriction)
        return request.files.firstNotNullOfOrNull { policy.refusal(it.name, it.mimeType, it.sizeInBytes) }?.name
    }

    private suspend fun stored(conversationId: ConversationId, messageId: String) =
        database.messageDAO.getMessageById(messageId, conversationId.toDao()) != null

    /** Null when [block] failed. Cancellation leaves claimed entries for the next server. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> guarded(step: String, block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        kaliumLogger.w("Share outbox step failed: $step", failure)
        null
    }

    private companion object {
        const val UNREADABLE = "UNREADABLE"
        const val FAILED = "FAILED"
    }
}
