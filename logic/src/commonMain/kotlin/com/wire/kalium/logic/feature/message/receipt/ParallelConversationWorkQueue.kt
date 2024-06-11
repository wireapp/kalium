/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.message.receipt

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.id.ConversationId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A work queue that schedules and runs actions for conversations based on a time
 */
internal interface ConversationWorkQueue {
    /**
     * Enqueue [worker] to perform on the provided [input], to be executed according to the implementation.
     */
    suspend fun enqueue(input: ConversationTimeEventInput, worker: ConversationTimeEventWorker)
}

/**
 * This implementation of [ConversationWorkQueue] allows parallel work between conversations,
 * but _only one_ simultaneous work per conversation, _i.e._ each conversation has its own parallel queue.
 *
 * If work is being performed for a conversation, allows enqueuing up to _one_ extra work to be done afterward.
 * When attempting to enqueue multiple works for the same [ConversationTimeEventInput.conversationId],
 * only the work with most recent [ConversationTimeEventInput.time] parameter will be scheduled.
 *
 * This is aimed at things like handling Delivery or Read Receipts.
 * For Read Receipts, for example, the user may navigate to an unread conversation, and the client might want to mark as read multiple
 * times in a row as the user scrolls through the unread messages.
 * This queue solves it by making sure only one event for that conversation is handled at a time, and if multiple calls are made in
 * rapid succession while one operation is ongoing, only the most recent one will actually be scheduled to be performed.
 *
 * @property scope The CoroutineScope used for enqueuing worker coroutines.
 * @property dispatcher The dispatcher for executing the works.
 */
internal class ParallelConversationWorkQueue(
    private val scope: CoroutineScope,
    kaliumLogger: KaliumLogger,
    private val dispatcher: CoroutineDispatcher,
) : ConversationWorkQueue {
    private val mutex = Mutex()
    private val conversationQueueMap = mutableMapOf<ConversationId, MutableStateFlow<ConversationTimeEventWork>>()
    private val logger = kaliumLogger.withTextTag("ConversationReceiptQueue")

    /**
     * Enqueues new work parameters for the provided [input].
     * Will **only** emit a new entry / replacing an existing one for the [ConversationTimeEventInput.conversationId] if
     * the [ConversationTimeEventInput.time] is more recent than the existing one.
     * If there's an existing work being done for the same [ConversationTimeEventInput.conversationId], it will wait until it's over.
     * After that, will start working as soon as the [dispatcher] allows it, while the [scope] is alive.
     */
    override suspend fun enqueue(
        input: ConversationTimeEventInput,
        worker: ConversationTimeEventWorker
    ): Unit = mutex.withLock {
        val conversationId = input.conversationId
        val time = input.time
        logger.v("Attempting to enqueue receipt work for conversation '${conversationId.toLogString()}', with time '$time'")
        val work = ConversationTimeEventWork(input, worker)
        val existingConversationQueue = conversationQueueMap[conversationId]
        existingConversationQueue?.let {
            val isNewWorkMoreRecent = time > it.value.conversationTimeEventInput.time
            if (isNewWorkMoreRecent) {
                it.value = work
            }
        } ?: createQueueForConversation(work).also {
            conversationQueueMap[conversationId] = it
        }
    }

    private suspend fun createQueueForConversation(initialWork: ConversationTimeEventWork) =
        MutableStateFlow(initialWork).also {
            scope.launch(dispatcher) {
                @Suppress("TooGenericExceptionCaught")
                it.collect { work ->
                    try {
                        work.worker.doWork(work.conversationTimeEventInput)
                    } catch (t: Throwable) {
                        logger.w("Failure in conversation work queue", t)
                    }
                }
            }
        }
}
