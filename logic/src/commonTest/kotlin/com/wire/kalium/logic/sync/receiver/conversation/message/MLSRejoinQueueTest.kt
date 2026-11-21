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
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.feature.message.StaleEpochVerifier
import com.wire.kalium.logic.framework.TestConversation
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

class MLSRejoinQueueTest {

    @Test
    fun givenQueuedRejoins_whenRejoining_thenEachRunsInItsOwnTransactionAndIsAcknowledged() = runTest {
        withTestUserDatabase(USER) { database ->
            val arrangement = Arrangement(database).withVerifyEpoch(FIRST, Either.Right(Unit)).withVerifyEpoch(SECOND, Either.Right(Unit))

            arrangement.queue.request(FIRST)
            arrangement.queue.request(SECOND)
            arrangement.queue.rejoinQueued()

            verifySuspend(VerifyMode.exactly(2)) {
                arrangement.cryptoTransactionProvider.transaction<Unit>("rejoinStaleMLSGroup", any())
            }
            verifySuspend(VerifyMode.exactly(1)) { arrangement.staleEpochVerifier.verifyEpoch(any(), FIRST, null, any()) }
            verifySuspend(VerifyMode.exactly(1)) { arrangement.staleEpochVerifier.verifyEpoch(any(), SECOND, null, any()) }
            assertEquals(emptyList(), arrangement.queued())
        }
    }

    @Test
    fun givenRejoinCanSucceedLater_whenRejoining_thenItStaysQueuedUntilTheNextSyncStart() = runTest {
        val serverError = KaliumException.ServerError(GenericAPIErrorResponse(500, "down", "server_error"))
        val failures = listOf(
            NetworkFailure.NoNetworkConnection(null),
            MLSFailure.TransportUnavailable,
            NetworkFailure.ServerMiscommunication(serverError),
            NetworkFailure.FederatedBackendFailure.FailedDomains(listOf("other.example")),
            NetworkFailure.FederatedBackendFailure.General("federation-remote-error"),
        )
        withTestUserDatabase(USER) { database ->
            val arrangement = Arrangement(database)
            failures.forEachIndexed { index, failure ->
                val conversationId = ConversationId("retryable-$index", "domain")
                arrangement.withVerifyEpoch(conversationId, Either.Left(failure))
                arrangement.queue.request(conversationId)

                arrangement.queue.rejoinQueued()
                arrangement.queue.request(conversationId)
                arrangement.queue.rejoinQueued()
                assertEquals(listOf(conversationId), arrangement.queued())
                verifySuspend(VerifyMode.exactly(1)) { arrangement.staleEpochVerifier.verifyEpoch(any(), conversationId, null, any()) }

                arrangement.withVerifyEpoch(conversationId, Either.Right(Unit))
                arrangement.queue.rejoinAtSyncStart()
                assertEquals(emptyList(), arrangement.queued())
            }
        }
    }

    @Test
    fun givenRejoinFailsForGood_whenRejoining_thenItIsDropped() = runTest {
        val failures = listOf(
            NetworkFailure.ServerMiscommunication(TestNetworkException.accessDenied),
            NetworkFailure.MlsMessageRejectedFailure.StaleMessage,
            NetworkFailure.FederatedBackendFailure.ConflictingBackends(listOf("other.example")),
            NetworkFailure.FederatedBackendFailure.FederationDenied("federation-denied"),
            NetworkFailure.FederatedBackendFailure.FederationNotEnabled("federation-not-enabled"),
            NetworkFailure.FederatedBackendFailure.FederationNotImplemented("federation-not-implemented"),
        )
        withTestUserDatabase(USER) { database ->
            val arrangement = Arrangement(database)
            failures.forEachIndexed { index, failure ->
                val conversationId = ConversationId("terminal-$index", "domain")
                arrangement.withVerifyEpoch(conversationId, Either.Left(failure))
                arrangement.queue.request(conversationId)

                arrangement.queue.rejoinQueued()

                assertEquals(emptyList(), arrangement.queued())
            }
        }
    }

    @Test
    fun givenQueuedRejoins_whenSyncStarts_thenOnlyGroupsCoreCryptoLacksAreRejoined() = runTest {
        withTestUserDatabase(USER) { database ->
            val arrangement = Arrangement(database).withVerifyEpoch(FIRST, Either.Right(Unit)).withVerifyEpoch(SECOND, Either.Right(Unit))
                .withLocalGroup(FIRST, exists = true).withLocalGroup(SECOND, exists = false)
            arrangement.queue.request(FIRST)
            arrangement.queue.request(SECOND)

            arrangement.queue.rejoinAtSyncStart()

            verifySuspend(VerifyMode.not) { arrangement.staleEpochVerifier.verifyEpoch(any(), FIRST, null, any()) }
            verifySuspend(VerifyMode.exactly(1)) { arrangement.staleEpochVerifier.verifyEpoch(any(), SECOND, null, any()) }
            assertEquals(emptyList(), arrangement.queued())
        }
    }

    private class Arrangement(
        private val database: TestUserDatabase,
    ) : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMokkeryImpl() {
        val staleEpochVerifier = mock<StaleEpochVerifier>()
        val conversationRepository = mock<ConversationRepository>()
        val mlsConversationRepository = mock<MLSConversationRepository>()
        val queue = MLSRejoinQueueImpl(
            database.builder.pendingActionDAO,
            cryptoTransactionProvider,
            staleEpochVerifier,
            conversationRepository,
            mlsConversationRepository,
        )

        init {
            everySuspend { conversationRepository.getConversationProtocolInfo(any()) } returns Either.Left(StorageFailure.DataNotFound)
        }

        suspend fun withLocalGroup(conversationId: ConversationId, exists: Boolean) = apply {
            val groupId = GroupID(conversationId.value)
            everySuspend { conversationRepository.getConversationProtocolInfo(conversationId) } returns
                Either.Right(TestConversation.MLS_PROTOCOL_INFO.copy(groupId = groupId))
            everySuspend { mlsConversationRepository.hasEstablishedMLSGroup(any(), groupId) } returns Either.Right(exists)
        }

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
