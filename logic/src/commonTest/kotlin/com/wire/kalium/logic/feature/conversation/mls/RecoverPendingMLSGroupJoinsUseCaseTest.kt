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
package com.wire.kalium.logic.feature.conversation.mls

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.MLSJoinMode
import com.wire.kalium.logic.data.conversation.mls.PendingActionsRepository
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.SyncStateObserver
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.network.api.model.GenericAPIErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.persistence.dao.pendingaction.PendingActionEntity
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RecoverPendingMLSGroupJoinsUseCaseTest {
    @Test
    fun waitsForLiveBeforeReadingRecoveryWork() = runTest {
        val a = Arrangement().defaults()
        everySuspend { a.syncStateObserver.waitUntilLiveOrFailure() } returns Either.Left(CoreFailure.MissingClientRegistration)
        a.useCase()()
        verifySuspend(VerifyMode.not) { a.pending.getPendingMLSGroupJoinEntries() }
    }

    @Test
    fun acknowledgesOnlyAnEstablishedOriginalWithItsNativeGroup() = runTest {
        val a = Arrangement().defaults()
        a.useCase()()
        verifySuspend { a.join(any(), TestConversation.ID, any(), true, MLSJoinMode.RECOVER_EXACT_GROUP) }
        verifySuspend { a.pending.acknowledgeMLSGroupJoin(ENTRY) }
        verifySuspend(VerifyMode.not) { a.pending.pauseMLSGroupJoin(any()) }
    }

    @Test
    fun successfulJoinThatLeavesOriginalPendingPausesUntilNewEvidence() = runTest {
        val a = Arrangement().defaults()
        everySuspend { a.conversations.getNonDeletedConversationById(any()) } returns Either.Right(PENDING)
        everySuspend { a.mlsContext.conversationExists(any()) } returns false
        a.useCase()()
        verifySuspend { a.pending.pauseMLSGroupJoin(ENTRY) }
        verifySuspend(VerifyMode.not) { a.pending.acknowledgeMLSGroupJoin(any()) }
    }

    @Test
    fun transientFailureRetainsAndRotatesTheAttemptWithoutImmediatelyRetrying() = runTest {
        val a = Arrangement().defaults()
        everySuspend { a.join(any(), any(), any(), true, MLSJoinMode.RECOVER_EXACT_GROUP) } returns
            Either.Left(NetworkFailure.NoNetworkConnection(null))
        a.useCase()()
        verifySuspend { a.pending.retryMLSGroupJoin(ENTRY) }
        verifySuspend(VerifyMode.not) { a.pending.acknowledgeMLSGroupJoin(any()) }
        assertEquals(0, a.signals)
    }

    @Test
    fun unavailableRecoveryPausesWithoutHidingHistoryOrDeletingNewerEvidence() = runTest {
        for (code in listOf(403, 404)) {
            val a = Arrangement().defaults()
            val label = if (code == 403) "access-denied" else "no-conversation"
            val error = KaliumException.InvalidRequestError(GenericAPIErrorResponse(code, "removed", label))
            val failure = NetworkFailure.ServerMiscommunication(error)
            everySuspend { a.join(any(), any(), any(), true, MLSJoinMode.RECOVER_EXACT_GROUP) } returns Either.Left(failure)
            a.useCase()()
            verifySuspend { a.pending.pauseMLSGroupJoin(ENTRY) }
            verifySuspend(VerifyMode.not) {
                a.conversations.setConversationDeletedLocally(any(), any())
                a.pending.acknowledgeMLSGroupJoin(any(), any())
            }
        }
    }

    @Test
    fun discoversMissingPendingGroupsEvenWithoutAnEvent() = runTest {
        val a = Arrangement().defaults()
        everySuspend { a.pending.getPendingMLSGroupJoinEntries() } returns emptyList()
        everySuspend { a.conversations.getConversationsByGroupState(GroupState.PENDING_WELCOME_MESSAGE) } returns
            Either.Right(listOf(PENDING))
        everySuspend { a.mlsContext.conversationExists(any()) } returns false
        a.useCase()()
        verifySuspend { a.pending.discoverMLSGroupJoin(PENDING.id) }
    }

    @Test
    fun legacyPendingJoinAtEpochZeroCanEstablishWithoutNewEvidence() = runTest {
        val a = Arrangement().defaults()
        var conversation = PENDING.copy(protocol = (PENDING.protocol as Conversation.ProtocolInfo.MLS).copy(
            epoch = 0UL, groupState = GroupState.PENDING_JOIN))
        everySuspend { a.pending.getPendingMLSGroupJoinEntries() } returns emptyList()
        everySuspend { a.pending.getPendingMLSGroupJoinEntries(false) } returns listOf(ENTRY)
        everySuspend { a.conversations.getNonDeletedConversationById(any()) } calls { Either.Right(conversation) }
        everySuspend { a.join(any(), any(), any(), true, MLSJoinMode.JOIN_OR_ESTABLISH) } calls {
            assertEquals(GroupState.PENDING_JOIN, (conversation.protocol as Conversation.ProtocolInfo.MLS).groupState)
            assertEquals(0UL, (conversation.protocol as Conversation.ProtocolInfo.MLS).epoch)
            conversation = ESTABLISHED
            Either.Right(Unit)
        }
        a.useCase()()
        verifySuspend { a.join(any(), TestConversation.ID, any(), true, MLSJoinMode.JOIN_OR_ESTABLISH) }
        verifySuspend { a.pending.acknowledgeMLSGroupJoin(ENTRY, false) }
    }

    @Test
    fun failedLegacyEstablishmentRemainsRetryableWithoutNewMessageEvidence() = runTest {
        val a = Arrangement().defaults()
        everySuspend { a.pending.getPendingMLSGroupJoinEntries() } returns emptyList()
        everySuspend { a.pending.getPendingMLSGroupJoinEntries(false) } returns listOf(ENTRY)
        everySuspend { a.join(any(), any(), any(), true, MLSJoinMode.JOIN_OR_ESTABLISH) } returns
            Either.Left(CoreFailure.Unknown(IllegalStateException("retry creation")))
        a.useCase()()
        verifySuspend { a.pending.retryMLSGroupJoin(ENTRY, false) }
        verifySuspend(VerifyMode.not) { a.pending.pauseMLSGroupJoin(any()) }
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val pending = mock<PendingActionsRepository>(MockMode.autoUnit)
        val syncStateObserver = mock<SyncStateObserver>(MockMode.autoUnit)
        val join = mock<JoinExistingMLSConversationUseCase>(MockMode.autoUnit)
        val conversations = mock<ConversationRepository>(MockMode.autoUnit)
        var signals = 0

        suspend fun defaults() = apply {
            withTransactionReturning(Either.Right(Unit))
            everySuspend { syncStateObserver.waitUntilLiveOrFailure() } returns Either.Right(Unit)
            everySuspend { pending.getPendingMLSGroupJoinEntries() } returns listOf(ENTRY)
            everySuspend { pending.getPendingMLSGroupJoinEntries(false) } returns emptyList()
            everySuspend { pending.getPausedMLSGroupJoins() } returns emptyList()
            everySuspend { pending.discoverMLSGroupJoin(any()) } returns true
            everySuspend { join(any(), any(), any(), true, MLSJoinMode.RECOVER_EXACT_GROUP) } returns Either.Right(Unit)
            everySuspend { conversations.getConversationsByGroupState(any()) } returns Either.Right(emptyList())
            everySuspend { conversations.getNonDeletedConversationById(any()) } returns Either.Right(ESTABLISHED)
            everySuspend { conversations.getConversationMembers(any()) } returns Either.Right(listOf(TestUser.USER_ID))
            everySuspend { conversations.setConversationDeletedLocally(any(), true) } returns Either.Right(Unit)
            everySuspend { mlsContext.conversationExists(any()) } returns true
        }

        fun useCase() = RecoverPendingMLSGroupJoinsUseCaseImpl(
            pending, syncStateObserver, cryptoTransactionProvider, join, conversations, TestUser.USER_ID, { signals++ }
        )
    }

    private companion object {
        val ENTRY = PendingActionEntity(TestConversation.ID.toDao(), 1L)
        val ESTABLISHED = TestConversation.GROUP(TestConversation.MLS_PROTOCOL_INFO.copy(groupState = GroupState.ESTABLISHED))
        val PENDING = TestConversation.GROUP(TestConversation.MLS_PROTOCOL_INFO.copy(groupState = GroupState.PENDING_WELCOME_MESSAGE))
    }
}

private typealias GroupState = Conversation.ProtocolInfo.MLSCapable.GroupState
