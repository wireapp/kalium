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

package com.wire.kalium.share

import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.IOException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The extension's side of the app's share outbox (ADR 13), for accounts [KaliumShareSender] finds `BUSY` because the
 * app owns them. [rootPath] is [KaliumShareConfiguration.rootPath].
 */
public class KaliumShareOutbox(private val rootPath: String) {

    /**
     * Asks the app for an account's conversations. The host then rings the app, and reads the answer with
     * [appConversations]. When the app does not answer, something else holds the account briefly.
     */
    public fun askAppForConversations(userValue: String, userDomain: String) {
        readingFiles(Unit) { outbox(userValue, userDomain).askForConversations() }
    }

    /** The app's answer to [askAppForConversations], once; null until the app answered. */
    public fun appConversations(userValue: String, userDomain: String): KaliumShareConversations? =
        readingFiles(null) { outbox(userValue, userDomain).takeConversations()?.toKalium() }

    /**
     * Hands [request] to the app, which owns the account (ADR 13), and returns the handoff's ID. The host then rings the
     * app and follows the handoff with [handOffStatus]. The app sends with its own budget and alerts, so
     * [KaliumShareRequest.budgetMilliseconds] and [KaliumShareRequest.showMessageContent] do not apply. Null when the
     * request is invalid or a file cannot be copied.
     */
    @OptIn(ExperimentalUuidApi::class)
    @Throws(CancellationException::class)
    public suspend fun handOff(request: KaliumShareRequest): String? = withContext(Dispatchers.Default) {
        readingFiles(null) {
            val files = request.attachments.map { it.toShareFile() ?: return@readingFiles null }
            if (files.size > MAX_ATTACHMENTS || request.text.isBlank() && files.isEmpty()) return@readingFiles null
            val outboxRequest = OutboxRequest(
                conversationValue = request.conversationValue,
                conversationDomain = request.conversationDomain,
                files = files.map {
                    OutboxFile(Uuid.random().toString(), it.name, it.mimeType, it.sizeInBytes, it.width, it.height, it.durationMs)
                },
                text = request.text,
                textMessageId = Uuid.random().toString(),
            )
            outbox(request.userValue, request.userDomain).handOff(outboxRequest, files.map { it.path })
        }
    }

    /** Where a handoff is. Once the app finished it, the result is reported once. Unreadable files read as waiting. */
    public fun handOffStatus(userValue: String, userDomain: String, handOffId: String): KaliumShareHandOff =
        when (val state = readingFiles(HandOffState.Waiting) { outbox(userValue, userDomain).state(handOffId) }) {
            HandOffState.Waiting -> KaliumShareHandOff(KaliumShareHandOffStatus.WAITING)
            HandOffState.Claimed -> KaliumShareHandOff(KaliumShareHandOffStatus.CLAIMED)
            is HandOffState.Finished -> if (state.result == ShareOutbox.STORED) {
                KaliumShareHandOff(KaliumShareHandOffStatus.STORED)
            } else {
                KaliumShareHandOff(KaliumShareHandOffStatus.NOT_STORED, state.result)
            }
        }

    private fun outbox(userValue: String, userDomain: String) = ShareOutbox(rootPath, UserId(userValue, userDomain))

    /** A Kotlin exception would end the extension, so a file the outbox cannot read or write gives [fallback]. */
    private inline fun <T> readingFiles(fallback: T, block: () -> T): T = try {
        block()
    } catch (failure: IOException) {
        kaliumLogger.w("Share outbox unavailable", failure)
        fallback
    }
}

public enum class KaliumShareHandOffStatus {
    /** The app has not claimed the share yet. */
    WAITING,

    /** The app is storing the share. */
    CLAIMED,

    /** The app stored every message, and sends them as it sends the composer's. */
    STORED,

    /** The app stored nothing more; [KaliumShareHandOff.failureReason] says why. */
    NOT_STORED,
}

public data class KaliumShareHandOff(
    public val status: KaliumShareHandOffStatus,
    /** A share rule (as `send` reports them), `FAILED`, `UNREADABLE`, or `MISSING` once the result was taken. */
    public val failureReason: String? = null,
)
