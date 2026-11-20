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
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.feature.message.StaleEpochVerifier
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMokkeryImpl
import com.wire.kalium.network.api.model.GenericAPIErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.withTestUserDatabase
import com.wire.kalium.persistence.dao.pendingaction.PendingActionType
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MLSRejoinQueueTest {

    @Test
    fun givenQueuedRejoins_whenRejoining_thenEachRunsInItsOwnTransactionAndIsNotQueuedAgainUntilABatchIsAcknowledged() =
        runTest {
            withTestUserDatabase(USER) { database ->
                val arrangement = Arrangement(database).withVerifyEpoch(FIRST, Either.Right(Unit)).withVerifyEpoch(SECOND, Either.Right(Unit))
                val queue = arrangement.queue

                assertTrue(queue.request(FIRST))
                assertTrue(queue.request(SECOND))
                assertTrue(queue.rejoinQueued())

                verifySuspend(VerifyMode.exactly(2)) {
                    arrangement.cryptoTransactionProvider.transaction<Unit>("rejoinStaleMLSGroup", any())
                }
                verifySuspend(VerifyMode.exactly(1)) { arrangement.staleEpochVerifier.verifyEpoch(any(), FIRST, null, any()) }
                verifySuspend(VerifyMode.exactly(1)) { arrangement.staleEpochVerifier.verifyEpoch(any(), SECOND, null, any()) }
                assertEquals(emptyList(), arrangement.queued())
                // The replayed batch ignores the rejoined groups' old messages instead of queueing them again.
                assertFalse(queue.request(FIRST))
                assertFalse(queue.rejoinQueued())

                queue.onBatchAcknowledged()
                assertTrue(queue.request(FIRST))
            }
        }

    @Test
    fun givenRejoinCanSucceedLater_whenRejoining_thenItThrowsAndKeepsTheRejoinQueued() = runTest {
        val serverError = KaliumException.ServerError(GenericAPIErrorResponse(500, "down", "server_error"))
        val failures = listOf(
            NetworkFailure.NoNetworkConnection(null),
            MLSFailure.TransportUnavailable,
            NetworkFailure.ServerMiscommunication(serverError),
            NetworkFailure.FederatedBackendFailure.FailedDomains(listOf("other.example")),
        )
        withTestUserDatabase(USER) { database ->
            val arrangement = Arrangement(database)
            failures.forEachIndexed { index, failure ->
                val conversationId = ConversationId("retryable-$index", "domain")
                arrangement.withVerifyEpoch(conversationId, Either.Left(failure))
                arrangement.queue.request(conversationId)

                val thrown = runCatching { arrangement.queue.rejoinQueued() }.exceptionOrNull()

                assertEquals(failure, (thrown as KaliumSyncException).coreFailureCause)
                assertEquals(listOf(conversationId), arrangement.queued())
                assertTrue(arrangement.queue.request(conversationId))
                arrangement.withVerifyEpoch(conversationId, Either.Right(Unit))
                arrangement.queue.rejoinQueued()
            }
        }
    }

    @Test
    fun givenRejoinFailsForGood_whenRejoining_thenItIsGivenUpAndItsMessagesAreIgnored() = runTest {
        val failures = listOf(
            NetworkFailure.ServerMiscommunication(TestNetworkException.accessDenied),
            NetworkFailure.MlsMessageRejectedFailure.StaleMessage,
            NetworkFailure.FederatedBackendFailure.ConflictingBackends(listOf("other.example")),
        )
        withTestUserDatabase(USER) { database ->
            val arrangement = Arrangement(database)
            failures.forEachIndexed { index, failure ->
                val conversationId = ConversationId("terminal-$index", "domain")
                arrangement.withVerifyEpoch(conversationId, Either.Left(failure))
                arrangement.queue.request(conversationId)

                assertTrue(arrangement.queue.rejoinQueued())

                assertEquals(emptyList(), arrangement.queued())
                assertFalse(arrangement.queue.request(conversationId))
            }
        }
    }

    private class Arrangement(
        private val database: TestUserDatabase,
    ) : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMokkeryImpl() {
        val staleEpochVerifier = mock<StaleEpochVerifier>()
        val queue = MLSRejoinQueueImpl(database.builder.pendingActionDAO, cryptoTransactionProvider, staleEpochVerifier)

        suspend fun withVerifyEpoch(conversationId: ConversationId, result: Either<CoreFailure, Unit>) = apply {
            withTransactionReturning(result)
            everySuspend { staleEpochVerifier.verifyEpoch(any(), conversationId, any(), any()) } returns result
        }

        suspend fun queued() = database.builder.pendingActionDAO.getByActionType(PendingActionType.REJOIN_STALE_MLS_GROUP)
            .map { ConversationId(it.qualifiedId.value, it.qualifiedId.domain) }
    }

    private companion object {
        val USER = QualifiedID("mls-rejoin-queue", "domain").toDao()
        val FIRST = ConversationId("first", "domain")
        val SECOND = ConversationId("second", "domain")
    }
}
