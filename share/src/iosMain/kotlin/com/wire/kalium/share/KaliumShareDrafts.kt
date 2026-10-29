/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.share

import com.wire.kalium.common.logger.kaliumLogger
import okio.IOException
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

/** The extension observes this store; only the process owning the account uploads or publishes its drafts. */
public class KaliumShareDrafts(rootPath: String, userValue: String, userDomain: String) {
    private val store = ShareDraftStore(rootPath, UserId(userValue, userDomain))
    private val outbox = ShareOutbox(rootPath, UserId(userValue, userDomain))

    public fun create(conversationValue: String, conversationDomain: String, file: KaliumShareAttachment): String? =
        readingFiles(null) {
            file.toShareFile()?.let { store.create(ConversationId(conversationValue, conversationDomain), it) }
        }

    public fun state(id: String): KaliumShareDraftState = readingFiles(KaliumShareDraftState(0.0, false, "DRAFT_UNAVAILABLE")) {
        val record = store.read(id)
        KaliumShareDraftState(
            progress = record?.progress ?: 0.0,
            ready = record?.asset != null && !store.isCancelled(id),
            failure = record?.failure ?: "DRAFT_UNAVAILABLE".takeIf { record == null },
        )
    }

    public fun fail(id: String, reason: String) {
        readingFiles(Unit) { store.read(id)?.let { store.write(id, it.copy(failure = reason)) } }
    }

    /** Mark first: a late upload completion must discard rather than become ready. */
    public fun cancel(id: String) { readingFiles(Unit) { store.cancel(id) } }

    /** The caller rings the existing share doorbell, then observes [state]. */
    @Throws(CancellationException::class)
    public suspend fun handOff(id: String, discard: Boolean): String? = withContext(Dispatchers.Default) {
        readingFiles(null) {
            val record = store.read(id) ?: return@readingFiles null
            outbox.handOff(
                OutboxRequest(
                    record.conversationValue,
                    record.conversationDomain,
                    if (discard) emptyList() else listOf(
                        OutboxFile(
                            Uuid.random().toString(),
                            record.file.name,
                            record.file.mimeType,
                            record.file.sizeInBytes,
                            record.file.width,
                            record.file.height,
                            record.file.durationMs
                        )
                    ),
                    text = "",
                    textMessageId = Uuid.random().toString(),
                    draftAction = if (discard) "discard" else "prepare",
                    draftId = id,
                ),
                if (discard) emptyList() else listOf(record.file.path),
            )
        }
    }

    private inline fun <T> readingFiles(fallback: T, block: () -> T): T = try {
        block()
    } catch (failure: IOException) {
        kaliumLogger.w("Share draft storage unavailable", failure)
        fallback
    }

}

public data class KaliumShareDraftState(public val progress: Double, public val ready: Boolean, public val failure: String?)
