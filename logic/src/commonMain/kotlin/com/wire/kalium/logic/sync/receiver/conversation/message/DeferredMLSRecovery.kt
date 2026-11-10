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

package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.conversation.ResetMLSConversationResult
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCase
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.feature.message.StaleEpochVerifier
import com.wire.kalium.logic.sync.SyncStateObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant

/**
 * Recovering an out-of-sync MLS group sends an external commit or resets the group on the backend. The receive batch
 * rolls back its crypto state when it fails, but those backend changes stay, so a retried batch would send them again.
 *
 * The receive path gets [staleEpochVerifier] and [resetMLSConversation] from here. They only record the request;
 * [run] carries the requests out once sync is live, each in its own transaction. A request made again while it is
 * waiting runs once.
 */
internal class DeferredMLSRecovery(
    private val syncStateObserver: SyncStateObserver,
    private val transactionProvider: CryptoTransactionProvider,
    private val verifier: StaleEpochVerifier,
    private val reset: ResetMLSConversationUseCase,
    logger: KaliumLogger = kaliumLogger,
) {
    private val logger = logger.withTextTag("DeferredMLSRecovery")
    private val mutex = Mutex()
    private val pending = LinkedHashMap<Request, Instant?>()
    private val signals = Channel<Unit>(Channel.CONFLATED)

    val staleEpochVerifier: StaleEpochVerifier = object : StaleEpochVerifier {
        override suspend fun verifyEpoch(
            transactionContext: CryptoTransactionContext,
            conversationId: ConversationId,
            subConversationId: SubconversationId?,
            timestamp: Instant?
        ): Either<CoreFailure, Unit> {
            enqueue(Request.VerifyEpoch(conversationId, subConversationId), timestamp)
            return Either.Right(Unit)
        }
    }

    val resetMLSConversation: ResetMLSConversationUseCase = object : ResetMLSConversationUseCase {
        @Deprecated("Transaction provider should be provided")
        override suspend fun invoke(conversationId: ConversationId): ResetMLSConversationResult = enqueueReset(conversationId)

        override suspend fun invoke(
            conversationId: ConversationId,
            transactionContext: CryptoTransactionContext
        ): ResetMLSConversationResult = enqueueReset(conversationId)
    }

    /** Runs for the lifetime of the session. */
    suspend fun run() {
        signals.consumeEach {
            syncStateObserver.waitUntilLive()
            val requests = mutex.withLock { pending.toList().also { pending.clear() } }
            requests.forEach { (request, timestamp) -> recover(request, timestamp) }
        }
    }

    // A failed attempt is dropped: the next message that fails in the same group asks again.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun recover(request: Request, timestamp: Instant?) {
        try {
            transactionProvider.transaction("deferredMLSRecovery") { context ->
                when (request) {
                    is Request.VerifyEpoch ->
                        verifier.verifyEpoch(context, request.conversationId, request.subconversationId, timestamp)

                    is Request.Reset -> reset(request.conversationId, context).toEither()
                }
            }.onFailure { logger.w("MLS recovery for ${request.conversationId.toLogString()} failed: $it") }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w("MLS recovery for ${request.conversationId.toLogString()} failed", e)
        }
    }

    private suspend fun enqueueReset(conversationId: ConversationId): ResetMLSConversationResult {
        enqueue(Request.Reset(conversationId), null)
        return ResetMLSConversationResult.Success
    }

    private suspend fun enqueue(request: Request, timestamp: Instant?) {
        mutex.withLock { pending[request] = timestamp }
        signals.trySend(Unit)
    }

    private sealed interface Request {
        val conversationId: ConversationId

        data class VerifyEpoch(
            override val conversationId: ConversationId,
            val subconversationId: SubconversationId?
        ) : Request

        data class Reset(override val conversationId: ConversationId) : Request
    }
}
