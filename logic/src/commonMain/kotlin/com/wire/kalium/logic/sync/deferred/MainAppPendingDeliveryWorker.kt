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
package com.wire.kalium.logic.sync.deferred

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.feature.message.confirmation.SendDeliverSignalUseCase
import com.wire.kalium.logic.sync.SyncStateObserver
import com.wire.kalium.logic.util.ExponentialDurationHelper
import com.wire.kalium.persistence.dao.message.PendingDeliveryConfirmationDAO
import com.wire.kalium.persistence.dao.message.PendingDeliveryConfirmationEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.time.Duration.Companion.seconds

/** The NSE records work before completion; the owned app scope sends it after sync is live. */
internal class MainAppPendingDeliveryWorker(
    private val pending: PendingDeliveryConfirmationDAO,
    private val conversations: ConversationRepository,
    private val sendDelivery: SendDeliverSignalUseCase,
    private val syncStateObserver: SyncStateObserver,
) {
    // The NSE cannot add records while this app scope owns the account. A fresh app scope
    // drains work left by the preceding extension attempt, including a terminated attempt.
    suspend fun run() {
        val backoff = ExponentialDurationHelper(1.seconds, 30.seconds)
        do {
            syncStateObserver.waitUntilLive()
            val result = drain()
            if (result is Either.Right) return
            kaliumLogger.w("Pending delivery confirmations will be retried")
            delay(backoff.next())
        } while (true)
    }

    /** A failed item stays durable, while independent confirmations in later pages can advance. */
    suspend fun drain(): Either<CoreFailure, Unit> {
        var afterId = 0L
        var firstFailure: CoreFailure? = null
        do {
            currentCoroutineContext().ensureActive()
            val page = when (val read = wrapStorageRequest { pending.getPendingAfter(afterId, PAGE_SIZE) }) {
                is Either.Left -> return read
                is Either.Right -> read.value
            }
            if (page.isEmpty()) break
            for (items in page.groupBy { it.conversationId }.values) {
                val result = sendAndAcknowledge(items)
                if (result is Either.Left && firstFailure == null) firstFailure = result.value
            }
            afterId = page.last().id
        } while (true)
        return firstFailure?.let { Either.Left(it) } ?: Either.Right(Unit)
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun sendAndAcknowledge(items: List<PendingDeliveryConfirmationEntity>): Either<CoreFailure, Unit> = try {
        conversations.getConversationById(items.first().conversationId.toModel()).flatMap { conversation ->
            // Apply the same current-conversation rule as the app's delivery queue.
            if (conversation.type == Conversation.Type.OneOnOne) {
                sendDelivery(conversation, items.map { it.messageId }).toEither()
            } else Either.Right(Unit)
        }.flatMap { wrapStorageRequest { pending.acknowledge(items.map { it.id }) } }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Either.Left(CoreFailure.Unknown(failure))
    }

    private companion object {
        const val PAGE_SIZE = 100L
    }
}
