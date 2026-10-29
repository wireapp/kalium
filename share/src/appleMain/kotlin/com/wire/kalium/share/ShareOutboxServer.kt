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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.message.CellAssetContent
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.featureFlags.BuildFileRestrictionState
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.util.InternalKaliumApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    public suspend fun prepareCells(
        conversationId: ConversationId,
        files: List<ShareOutboxFile>,
        progress: (Long, Long) -> Unit,
    ): Either<String, List<CellAssetContent>>
    public suspend fun discardCells(attachments: List<CellAssetContent>)
    public suspend fun storeCells(
        conversationId: ConversationId,
        attachments: List<CellAssetContent>,
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
    /**
     * Tells the user why the app stored no more of a share, when the extension stopped waiting for the answer. The
     * reason is a share rule, as `send` reports them, or `FAILED`.
     */
    private val refused: suspend (conversationId: ConversationId, conversationName: String?, reason: String) -> Unit,
) {
    private val outbox = ShareOutbox(rootPath, selfUserId)
    private val drafts = ShareDraftStore(rootPath, selfUserId)

    /**
     * Answers the extension now, then whenever [doorbell] rings, until cancelled. Requests for conversations are
     * answered on their own, so a share being imported never keeps a sheet waiting for its list. Shares are claimed
     * on every ring, so a sheet learns at once that the app has its share, even while an import waits for the account
     * to catch up; one import runs at a time.
     */
    public suspend fun serve(doorbell: Flow<Unit>): Unit = coroutineScope {
        guarded("sweep") { outbox.sweep() }
        launch { doorbell.onStart { emit(Unit) }.conflate().collect { answerConversations() } }
        val imports = Channel<Unit>(Channel.CONFLATED)
        launch { imports.receiveAsFlow().collect { importShares() } }
        doorbell.onStart { emit(Unit) }.conflate().collect {
            // Claims at once, so a sheet learns the app has its share while an import waits. importShares claims
            // again, which also lists what to import; both are needed.
            guarded("claim") { outbox.claim() }
            imports.send(Unit)
        }
    }

    internal suspend fun answerConversations() {
        guarded("conversations") { outbox.answerIfAsked { database.sharePicker(selfUserId, restriction) } }
    }

    internal suspend fun importShares() {
        for (id in guarded("claim") { outbox.claim() }.orEmpty()) {
            val request = outbox.request(id)
            // An import that fails unexpectedly would fail again, so it ends with what it stored.
            kaliumLogger.i("Share handoff stage=IMPORT_START")
            val result = if (request == null) UNREADABLE else guarded("import") { import(id, request) } ?: FAILED
            kaliumLogger.i("Share handoff stage=IMPORT_COMPLETE result=$result")
            val unwatched = guarded("finish") { outbox.finish(id, result) } == true
            if (request == null || request.draftAction != null) continue
            if (unwatched && result != ShareOutbox.STORED) {
                // The finished entry is gone, so a report cancelled now could not be retried.
                withContext(NonCancellable) { guarded("report") { report(request, result) } }
            }
        }
    }

    private suspend fun report(request: OutboxRequest, reason: String) {
        val conversationId = ConversationId(request.conversationValue, request.conversationDomain)
        refused(conversationId, database.conversationDAO.getConversationDetailsById(conversationId.toDao())?.name, reason)
    }

    /** Stores the share's items in order, skipping those an interrupted import already stored. */
    @Suppress("ReturnCount", "CyclomaticComplexMethod") // Route explicit outbox operations before ordinary messages.
    private suspend fun import(id: String, request: OutboxRequest): String {
        if (request.draftAction == "discard") return discardDraft(request)
        // Catching up can change the conversation or the account's file rules, so they are checked after it.
        sending.awaitSynced()
        val conversationId = ConversationId(request.conversationValue, request.conversationDomain)
        refusal(conversationId, request)?.let { return it }
        val isCells = database.conversationDAO.getConversationDetailsById(conversationId.toDao())?.wireCell != null
        if (request.draftAction == "prepare") return prepareDraft(id, request, conversationId, isCells)
        if (request.preparedDraftIds.isNotEmpty()) return storeDrafts(request, conversationId, isCells)
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

    private suspend fun discardDraft(request: OutboxRequest): String {
        request.draftId?.let { id ->
            drafts.read(id)?.let { record ->
                record.asset?.let { sending.discardCells(listOf(it.content(record.file))) }
            }
            drafts.consume(listOf(id))
        }
        return ShareOutbox.STORED
    }

    @Suppress("ReturnCount")
    private suspend fun prepareDraft(id: String, request: OutboxRequest, conversationId: ConversationId, isCells: Boolean): String {
        val draftId = request.draftId ?: return UNREADABLE
        if (!isCells || request.files.size != 1) return UNREADABLE
        val requestedDraft = drafts.read(draftId) ?: return UNREADABLE
        if (requestedDraft.conversationId != conversationId || !request.files.single().matches(requestedDraft.file)) return UNREADABLE
        prepareShareDraft(drafts, draftId, upload = { record, progress ->
            val file = request.files.single()
            val source = ShareOutboxFile(
                outbox.importCopy(id, 0),
                file.name,
                file.mimeType,
                file.sizeInBytes,
                file.width,
                file.height,
                file.durationMs,
            )
            when (val result = sending.prepareCells(conversationId, listOf(source), progress)) {
                is Either.Left -> result
                is Either.Right -> {
                    val asset = result.value.single()
                    val cell = database.conversationDAO.getConversationDetailsById(conversationId.toDao())?.wireCell
                    Either.Right(SharedDraftAsset(asset.id, asset.versionId, requireNotNull(asset.assetPath), requireNotNull(cell)))
                }
            }
        }, discard = { record ->
            record.asset?.let { sending.discardCells(listOf(it.content(record.file))) }
        })
        return ShareOutbox.STORED
    }

    @Suppress("ReturnCount")
    private suspend fun storeDrafts(request: OutboxRequest, conversationId: ConversationId, isCells: Boolean): String {
        if (!isCells) return ShareBlock.UNAVAILABLE.name
        if (stored(conversationId, request.textMessageId)) {
            drafts.consume(request.preparedDraftIds)
            return ShareOutbox.STORED
        }
        val records = drafts.resolve(request.preparedDraftIds, conversationId) ?: return UNREADABLE
        val cell = database.conversationDAO.getConversationDetailsById(conversationId.toDao())?.wireCell
        if (records.size != request.files.size || records.zip(request.files).any { (record, file) ->
                record.asset?.cellName != cell || !file.matches(record.file)
            }
        ) return UNREADABLE
        val attachments = records.map { requireNotNull(it.asset).content(it.file) }
        val failure = sending.storeCells(conversationId, attachments, request.text, request.textMessageId)
        if (failure == null) drafts.consume(request.preparedDraftIds)
        return failure ?: ShareOutbox.STORED
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

private fun OutboxFile.matches(file: ShareFile): Boolean =
    name == file.name && mimeType == file.mimeType && sizeInBytes == file.sizeInBytes &&
        width == file.width && height == file.height && durationMs == file.durationMs
