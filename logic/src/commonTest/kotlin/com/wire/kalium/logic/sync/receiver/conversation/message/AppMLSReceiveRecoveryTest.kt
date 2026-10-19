/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.cryptography.E2EIConversationState
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.mls.EpochChangesData
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventDeliveryInfo
import com.wire.kalium.logic.data.event.EventEnvelope
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.sync.incremental.EventSource
import com.wire.kalium.logic.sync.receiving.ReceivedPayloadIdentity
import com.wire.kalium.logic.sync.receiving.MLSReceiveRecovery
import com.wire.kalium.logic.sync.receiving.MLSReceiveRecoveryStore
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMokkeryImpl
import com.wire.kalium.network.api.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.event.NewEventEntity
import com.wire.kalium.util.ConversationPersistenceApi
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

@OptIn(ConversationPersistenceApi::class)
class AppMLSReceiveRecoveryTest {
    @Test
    fun appResumesDeletionWithoutCanonicalConversationAndRetiresItAfterAcknowledgement() = withArrangement { arrangement ->
        val envelope = arrangement.deletionEnvelope()
        val identity = checkNotNull(envelope.payloadIdentity)
        // A fresh store represents the NSE owner, whose SQL deletion survived native rollback.
        val nseStore = arrangement.freshStore()
        assertTrue(nseStore.prepareDeletion(identity, GROUP_ID))
        arrangement.database.builder.notificationEventStore.storePage(
            null, listOf(NewEventEntity(identity.eventId, "[]", false, false))
        )
        everySuspend { arrangement.mlsContext.conversationExists(GROUP_ID) } returns true
        everySuspend { arrangement.mlsContext.wipeConversation(GROUP_ID) } returns Unit

        assertFalse(arrangement.adapter.wereEffectsProcessed(arrangement.transactionContext, envelope))

        verifySuspend(VerifyMode.exactly(1)) { arrangement.mlsContext.wipeConversation(GROUP_ID) }
        verifySuspend(VerifyMode.not) { arrangement.conversations.getConversationProtocolInfo(any()) }
        verifySuspend(VerifyMode.not) { arrangement.mlsContext.readTransactionCheckpoint() }
        arrangement.adapter.acknowledge(listOf(identity.eventId))
        assertTrue(arrangement.database.builder.eventDAO.getEventById(identity.eventId)?.isProcessed == true)
        assertFalse(arrangement.freshStore().prepareDeletion(identity, null))
    }

    @Test
    fun appDeletionWithoutPendingIntentDoesNotTouchNativeState() = withArrangement { arrangement ->
        assertFalse(arrangement.adapter.wereEffectsProcessed(arrangement.transactionContext, arrangement.deletionEnvelope()))

        verifySuspend(VerifyMode.not) { arrangement.mlsContext.conversationExists(any()) }
        verifySuspend(VerifyMode.not) { arrangement.mlsContext.wipeConversation(any()) }
    }

    @Test
    fun appLeavesPresentConversationWipeToOrdinaryDeletionHandler() = withArrangement { arrangement ->
        val envelope = arrangement.deletionEnvelope()
        arrangement.store.prepareDeletion(checkNotNull(envelope.payloadIdentity), GROUP_ID)
        everySuspend { arrangement.conversations.getConversationById(any()) } returns Either.Right(TestConversation.CONVERSATION)

        assertFalse(arrangement.adapter.wereEffectsProcessed(arrangement.transactionContext, envelope))

        verifySuspend(VerifyMode.not) { arrangement.mlsContext.conversationExists(any()) }
        verifySuspend(VerifyMode.not) { arrangement.mlsContext.wipeConversation(any()) }
        assertTrue(arrangement.store.prepareDeletion(checkNotNull(envelope.payloadIdentity), null))
    }

    @Test
    fun appDeletionMatchesExactPayloadWithinOneEvent() = withArrangement { arrangement ->
        val first = arrangement.deletionEnvelope()
        val other = arrangement.deletionEnvelope("other-conversation")
        arrangement.store.prepareDeletion(checkNotNull(first.payloadIdentity), GROUP_ID)
        arrangement.store.prepareDeletion(checkNotNull(other.payloadIdentity), "other-group")
        everySuspend { arrangement.mlsContext.conversationExists(GROUP_ID) } returns true
        everySuspend { arrangement.mlsContext.wipeConversation(GROUP_ID) } returns Unit

        assertFalse(arrangement.adapter.wereEffectsProcessed(arrangement.transactionContext, first))

        verifySuspend(VerifyMode.exactly(1)) { arrangement.mlsContext.wipeConversation(GROUP_ID) }
        verifySuspend(VerifyMode.not) { arrangement.mlsContext.conversationExists("other-group") }
        verifySuspend(VerifyMode.not) { arrangement.mlsContext.wipeConversation("other-group") }
        assertTrue(arrangement.store.prepareDeletion(checkNotNull(other.payloadIdentity), null))
    }

    @Test
    fun appDeletionReplayWithAlreadyAbsentGroupKeepsIntentUntilAcknowledgement() = withArrangement { arrangement ->
        val envelope = arrangement.deletionEnvelope()
        val identity = checkNotNull(envelope.payloadIdentity)
        arrangement.store.prepareDeletion(identity, GROUP_ID)
        everySuspend { arrangement.mlsContext.conversationExists(GROUP_ID) } returns false

        repeat(2) { assertFalse(arrangement.adapter.wereEffectsProcessed(arrangement.transactionContext, envelope)) }

        verifySuspend(VerifyMode.not) { arrangement.mlsContext.wipeConversation(any()) }
        assertTrue(arrangement.freshStore().prepareDeletion(identity, null))
    }

    @Test
    fun ordinaryPreflightDoesNotReadCanonicalMetadataOrNativeCheckpoint() = withArrangement { arrangement ->

        assertFalse(arrangement.adapter.handle(arrangement.transactionContext, arrangement.event, false))

        verifySuspend(VerifyMode.not) { arrangement.conversations.getConversationProtocolInfo(any()) }
        verifySuspend(VerifyMode.not) { arrangement.api.fetchConversationDetails(any()) }
        verifySuspend(VerifyMode.not) { arrangement.mlsContext.readTransactionCheckpoint() }
    }

    @Test
    fun committedReceiptSkipsMetadataAndEffectsEvenIfGroupWasRemoved() = withArrangement { arrangement ->
        arrangement.completeRecovery()
        arrangement.store.recordProcessed(arrangement.mlsContext, listOf(arrangement.event))
        everySuspend { arrangement.mlsContext.conversationExists(any()) } returns false

        assertTrue(arrangement.adapter.handle(arrangement.transactionContext, arrangement.event, false))

        verifySuspend(VerifyMode.not) { arrangement.conversations.getConversationProtocolInfo(any()) }
        verifySuspend(VerifyMode.not) { arrangement.api.fetchConversationDetails(any()) }
        verifySuspend(VerifyMode.not) { arrangement.conversations.persistConversations(any()) }
    }

    @Test
    fun completedOperationWithoutReceiptRepairsEffectsAndPropagatesRequiredWriteFailure() = withArrangement { arrangement ->
        arrangement.completeRecovery()
        everySuspend { arrangement.api.fetchConversationDetails(any()) } returns NetworkResponse.Success(
            TestConversation.CONVERSATION_RESPONSE.copy(
                id = arrangement.event.conversationId.toApi(),
                protocol = ConvProtocol.MLS,
                groupId = GROUP_ID,
                epoch = 1uL,
                mlsCipherSuiteTag = 1,
            ),
            emptyMap(),
            200,
        )
        everySuspend { arrangement.conversations.persistConversations(any()) } returns Either.Left(StorageFailure.DataNotFound)

        val failure = assertFailsWith<KaliumSyncException> {
            arrangement.adapter.handle(arrangement.transactionContext, arrangement.event, false)
        }

        assertEquals(StorageFailure.DataNotFound, failure.coreFailureCause)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.conversations.persistConversations(any()) }
        verifySuspend(VerifyMode.not) { arrangement.conversations.getConversationProtocolInfo(any()) }
        verifySuspend(VerifyMode.not) { arrangement.api.fetchGroupInfo(any()) }
        verifySuspend(VerifyMode.not) { arrangement.persistMessage.invoke(any()) }
    }

    @Test
    fun currentNativeEpochHandlesWrongEpochWithoutJoiningOrCreatingAReceipt() = withArrangement { arrangement ->
        everySuspend { arrangement.conversations.getConversationProtocolInfo(any()) } returns Either.Right(
            TestConversation.MLS_PROTOCOL_INFO.copy(groupId = GroupID(GROUP_ID))
        )
        everySuspend { arrangement.api.fetchConversationDetails(any()) } returns NetworkResponse.Success(
            TestConversation.CONVERSATION_RESPONSE.copy(
                id = arrangement.event.conversationId.toApi(),
                protocol = ConvProtocol.MLS,
                groupId = GROUP_ID,
                epoch = 1uL,
                mlsCipherSuiteTag = 1,
            ),
            emptyMap(),
            200,
        )
        everySuspend { arrangement.mlsContext.conversationExists(GROUP_ID) } returns true
        everySuspend { arrangement.mlsContext.conversationEpoch(GROUP_ID) } returns 1uL

        assertTrue(arrangement.adapter.handle(arrangement.transactionContext, arrangement.event, true))
        assertEquals(null, arrangement.store.find(arrangement.event.id) { Base64.decode(arrangement.event.content) })
        verifySuspend(VerifyMode.exactly(1)) { arrangement.api.fetchConversationDetails(any()) }
        verifySuspend(VerifyMode.not) { arrangement.api.fetchGroupInfo(any()) }
        verifySuspend(VerifyMode.not) { arrangement.mlsContext.readTransactionCheckpoint() }
        verifySuspend(VerifyMode.not) { arrangement.conversations.persistConversations(any()) }
    }

    @Test
    fun verificationWarningFailureCanBeRetriedBeforeStatusChanges() = verificationRetry(false)

    @Test
    fun verificationFlagFailureCanBeRetriedBeforeStatusChanges() = verificationRetry(true)

    private fun verificationRetry(failFlag: Boolean) = withArrangement { arrangement ->
        arrangement.completeRecovery()
        everySuspend { arrangement.api.fetchConversationDetails(any()) } returns NetworkResponse.Success(
            TestConversation.CONVERSATION_RESPONSE.copy(
                id = arrangement.event.conversationId.toApi(), protocol = ConvProtocol.MLS,
                groupId = GROUP_ID, epoch = 1uL, mlsCipherSuiteTag = 1,
            ), emptyMap(), 200,
        )
        everySuspend { arrangement.conversations.persistConversations(any()) } returns Either.Right(Unit)
        everySuspend { arrangement.conversations.updateConversationMembers(any(), any(), any()) } returns Either.Right(Unit)
        everySuspend { arrangement.mlsConversations.updateGroupIdAndState(any(), any(), any(), any()) } returns Either.Right(Unit)
        everySuspend { arrangement.mlsContext.getGroupState(GROUP_ID) } returns E2EIConversationState.NOT_VERIFIED
        var status = Conversation.VerificationStatus.VERIFIED
        var fail = true
        var notified = true
        val messages = mutableMapOf<String, Message.Standalone>()
        everySuspend { arrangement.conversations.getGroupStatusMembersNamesAndHandles(any()) } calls {
            Either.Right(EpochChangesData(arrangement.event.conversationId, status, emptyMap()))
        }
        everySuspend { arrangement.conversations.updateMlsVerificationStatus(any(), any()) } calls {
            status = it.args[0] as Conversation.VerificationStatus
            Either.Right(Unit)
        }
        everySuspend { arrangement.persistMessage.invoke(any()) } calls {
            val message = it.args[0] as Message.Standalone
            if (fail && !failFlag) Either.Left(StorageFailure.DataNotFound) else {
                messages[message.id] = message
                Either.Right(Unit)
            }
        }
        everySuspend { arrangement.conversations.setDegradedConversationNotifiedFlag(any(), any()) } calls {
            if (fail && failFlag) Either.Left(StorageFailure.DataNotFound) else {
                notified = it.args[1] as Boolean
                Either.Right(Unit)
            }
        }

        assertFailsWith<KaliumSyncException> {
            arrangement.adapter.handle(arrangement.transactionContext, arrangement.event, false)
        }
        assertEquals(Conversation.VerificationStatus.VERIFIED, status)
        fail = false
        assertTrue(arrangement.adapter.handle(arrangement.transactionContext, arrangement.event, false))
        assertEquals(Conversation.VerificationStatus.DEGRADED, status)
        assertFalse(notified)
        assertEquals(1, messages.values.filterIsInstance<Message.System>().count {
            it.content == MessageContent.ConversationDegradedMLS
        })
        assertEquals(2, messages.size)
    }

    private fun withArrangement(block: suspend (Arrangement) -> Unit) = runTest(TestKaliumDispatcher.default) {
        val arrangement = Arrangement()
        try {
            block(arrangement)
        } finally {
            arrangement.database.builder.close()
            arrangement.database.delete()
        }
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMokkeryImpl() {
        val event = TestEvent.newMLSMessageEvent(Instant.DISTANT_PAST)
        val database = TestUserDatabase(TestUser.SELF.id.toDao(), TestKaliumDispatcher.default)
        val api = mock<ConversationApi>()
        val conversations = mock<ConversationRepository>()
        val persistMessage = mock<PersistMessageUseCase>()
        val mlsConversations = mock<MLSConversationRepository>()
        val store = MLSReceiveRecoveryStore(database.builder.metadataDAO, database.builder.notificationEventStore, "client")
        val adapter = AppMLSReceiveRecovery(
            MLSReceiveRecovery(store, api, TestUser.SELF.id) { _, _, _ -> error("Unexpected external join") },
            conversations,
            mlsConversations,
            persistMessage,
            TestUser.SELF.id,
            SelfTeamIdProvider { Either.Right(null) },
        )

        init {
            everySuspend { conversations.getConversationById(any()) } returns Either.Left(StorageFailure.DataNotFound)
        }

        fun freshStore() = MLSReceiveRecoveryStore(database.builder.metadataDAO, database.builder.notificationEventStore, "client")

        fun deletionEnvelope(conversationValue: String = event.conversationId.value): EventEnvelope {
            val conversationId = event.conversationId.copy(value = conversationValue)
            val deletion = Event.Conversation.DeletedConversation(
                "delete-payload", conversationId, TestUser.SELF.id, Instant.DISTANT_PAST
            )
            return EventEnvelope(
                deletion,
                EventDeliveryInfo(EventSource.PENDING),
                ReceivedPayloadIdentity(
                    "delete-notification",
                    EventContentDTO.Conversation.DeletedConversationDTO(
                        conversationId.toApi(), TestUser.SELF.id.toApi(), Instant.DISTANT_PAST.toString()
                    ),
                ),
            )
        }

        suspend fun completeRecovery() {
            var checkpoint: ByteArray? = null
            everySuspend { mlsContext.readTransactionCheckpoint() } calls { checkpoint }
            everySuspend { mlsContext.writeTransactionCheckpoint(any()) } calls { checkpoint = it.args[0] as ByteArray }
            everySuspend { mlsContext.conversationExists(GROUP_ID) } returns true
            everySuspend { mlsContext.conversationEpoch(GROUP_ID) } returns 2uL
            store.complete(mlsContext, store.begin(event.id, GROUP_ID, Base64.decode(event.content)))
        }
    }

    private companion object {
        const val GROUP_ID = "Z3JvdXA="
    }
}
