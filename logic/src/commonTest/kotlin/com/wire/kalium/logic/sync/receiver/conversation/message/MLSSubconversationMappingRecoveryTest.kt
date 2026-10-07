/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.DecryptedMessageBundle
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.SubconversationRepositoryImpl
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.feature.message.PendingProposalScheduler
import com.wire.kalium.logic.feature.message.StaleEpochVerifier
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMokkeryImpl
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.authenticated.conversation.SubconversationResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.model.GenericAPIErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.SubconversationDAO
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class MLSSubconversationMappingRecoveryTest {

    @Test
    fun missingSubgroupMappingIsRecoveredBeforeHandlerConsumesProposal() = withArrangement { arrangement ->
        val event = TestEvent.newMLSMessageEvent(EVENT_TIME, SUBCONVERSATION_ID)
        arrangement.remoteSubconversation(PARENT_ID, SUBCONVERSATION_ID, SUBGROUP_ID)
        everySuspend { arrangement.mlsContext.conversationExists(SUBGROUP_ID.value) } returns true

        arrangement.handler.handleNewMLSMessage(
            arrangement.transactionContext,
            event,
            TestEvent.liveDeliveryInfo,
        ).shouldSucceed()

        val reopenedRepository = SubconversationRepositoryImpl(
            arrangement.api,
            arrangement.database.builder.subconversationDAO,
        )
        assertEquals(SUBGROUP_ID, reopenedRepository.getSubconversationInfo(PARENT_ID, SUBCONVERSATION_ID))
        assertEquals(listOf(SUBGROUP_ID), arrangement.decryptionGroups)
        assertEquals(listOf(SUBGROUP_ID to EVENT_TIME.plus(COMMIT_DELAY.seconds)), arrangement.scheduledProposals)
        assertFalse(arrangement.parentFallbackAttempted)
        assertTrue(arrangement.proposalConsumed)
    }

    @Test
    fun mismatchedAuthenticatedSubgroupIdentityThrowsWithoutPersistingOrDecrypting() = withArrangement { arrangement ->
        val event = TestEvent.newMLSMessageEvent(EVENT_TIME, SUBCONVERSATION_ID)
        val mismatches = listOf(
            PARENT_ID.copy(domain = "other.example") to SUBCONVERSATION_ID,
            PARENT_ID to SubconversationId("other-subconversation"),
        )

        mismatches.forEach { (remoteParentId, remoteSubconversationId) ->
            arrangement.remoteSubconversation(remoteParentId, remoteSubconversationId, SUBGROUP_ID)

            val failure = assertFailsWith<KaliumSyncException> {
                arrangement.handler.handleNewMLSMessage(
                    arrangement.transactionContext,
                    event,
                    TestEvent.liveDeliveryInfo,
                )
            }
            assertEquals(MLSFailure.InvalidGroupId, failure.coreFailureCause)
            assertNull(arrangement.subconversations.getSubconversationInfo(PARENT_ID, SUBCONVERSATION_ID))
            assertTrue(arrangement.decryptionGroups.isEmpty())
            assertTrue(arrangement.scheduledProposals.isEmpty())
            assertFalse(arrangement.proposalConsumed)
        }
    }

    @Test
    fun missingNativeSubgroupDoesNotRestoreOrDecryptAndHandlerCanConsumeDelayedEvent() = withArrangement { arrangement ->
        val event = TestEvent.newMLSMessageEvent(EVENT_TIME, SUBCONVERSATION_ID)
        arrangement.remoteSubconversation(PARENT_ID, SUBCONVERSATION_ID, SUBGROUP_ID)
        everySuspend { arrangement.mlsContext.conversationExists(SUBGROUP_ID.value) } returns false

        arrangement.handler.handleNewMLSMessage(
            arrangement.transactionContext,
            event,
            TestEvent.liveDeliveryInfo,
        ).shouldSucceed()

        assertNull(arrangement.subconversations.getSubconversationInfo(PARENT_ID, SUBCONVERSATION_ID))
        assertTrue(arrangement.decryptionGroups.isEmpty())
        assertTrue(arrangement.scheduledProposals.isEmpty())
        assertFalse(arrangement.proposalConsumed)
    }

    @Test
    fun remoteFetchFailureThrowsWithoutPersistingOrDecrypting() = withArrangement { arrangement ->
        val event = TestEvent.newMLSMessageEvent(EVENT_TIME, SUBCONVERSATION_ID)
        arrangement.remoteSubconversationFailure()

        val failure = assertFailsWith<KaliumSyncException> {
            arrangement.handler.handleNewMLSMessage(
                arrangement.transactionContext,
                event,
                TestEvent.liveDeliveryInfo,
            )
        }

        assertIs<NetworkFailure.NoNetworkConnection>(failure.coreFailureCause)
        assertNull(arrangement.subconversations.getSubconversationInfo(PARENT_ID, SUBCONVERSATION_ID))
        assertTrue(arrangement.decryptionGroups.isEmpty())
        assertTrue(arrangement.scheduledProposals.isEmpty())
        assertFalse(arrangement.proposalConsumed)
    }

    @Test
    fun deletedRemoteConversationDoesNotRestoreOrDecryptAndHandlerCanConsumeDelayedEvent() = withArrangement { arrangement ->
        val event = TestEvent.newMLSMessageEvent(EVENT_TIME, SUBCONVERSATION_ID)
        arrangement.remoteConversationNotFound()

        arrangement.handler.handleNewMLSMessage(
            arrangement.transactionContext,
            event,
            TestEvent.liveDeliveryInfo,
        ).shouldSucceed()

        assertNull(arrangement.subconversations.getSubconversationInfo(PARENT_ID, SUBCONVERSATION_ID))
        assertTrue(arrangement.decryptionGroups.isEmpty())
        assertTrue(arrangement.scheduledProposals.isEmpty())
        assertFalse(arrangement.proposalConsumed)
    }

    @Test
    fun nativeLookupFailureThrowsWithoutPersistingOrDecrypting() = withArrangement { arrangement ->
        val event = TestEvent.newMLSMessageEvent(EVENT_TIME, SUBCONVERSATION_ID)
        arrangement.remoteSubconversation(PARENT_ID, SUBCONVERSATION_ID, SUBGROUP_ID)
        everySuspend { arrangement.mlsContext.conversationExists(SUBGROUP_ID.value) } calls {
            error("native lookup failed")
        }

        val failure = assertFailsWith<KaliumSyncException> {
            arrangement.handler.handleNewMLSMessage(
                arrangement.transactionContext,
                event,
                TestEvent.liveDeliveryInfo,
            )
        }

        assertIs<MLSFailure.Generic>(failure.coreFailureCause)
        assertNull(arrangement.subconversations.getSubconversationInfo(PARENT_ID, SUBCONVERSATION_ID))
        assertTrue(arrangement.decryptionGroups.isEmpty())
        assertTrue(arrangement.scheduledProposals.isEmpty())
        assertFalse(arrangement.proposalConsumed)
    }

    @Test
    fun mappingWriteFailurePreventsDecryptingOrConsumingProposal() = withArrangement(failMappingWrites = true) { arrangement ->
        val event = TestEvent.newMLSMessageEvent(EVENT_TIME, SUBCONVERSATION_ID)
        arrangement.remoteSubconversation(PARENT_ID, SUBCONVERSATION_ID, SUBGROUP_ID)
        everySuspend { arrangement.mlsContext.conversationExists(SUBGROUP_ID.value) } returns true

        assertFailsWith<IllegalStateException> {
            arrangement.handler.handleNewMLSMessage(
                arrangement.transactionContext,
                event,
                TestEvent.liveDeliveryInfo,
            )
        }

        assertNull(arrangement.subconversations.getSubconversationInfo(PARENT_ID, SUBCONVERSATION_ID))
        assertTrue(arrangement.decryptionGroups.isEmpty())
        assertTrue(arrangement.scheduledProposals.isEmpty())
        assertFalse(arrangement.proposalConsumed)
    }

    private fun withArrangement(
        failMappingWrites: Boolean = false,
        block: suspend (Arrangement) -> Unit,
    ) = runTest(TestKaliumDispatcher.default) {
        val arrangement = Arrangement(failMappingWrites)
        try {
            assertNull(
                arrangement.subconversations.getSubconversationInfo(PARENT_ID, SUBCONVERSATION_ID),
                "The test must begin with no durable subgroup mapping",
            )
            block(arrangement)
        } finally {
            arrangement.database.builder.close()
            arrangement.database.delete()
        }
    }

    private class Arrangement(
        failMappingWrites: Boolean,
    ) : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMokkeryImpl() {
        val database = TestUserDatabase(TestUser.SELF.id.toDao(), TestKaliumDispatcher.default)
        val api = mock<ConversationApi>()
        val conversations = mock<ConversationRepository>()
        val mlsConversations = mock<MLSConversationRepository>()
        val pendingProposals = mock<PendingProposalScheduler>()
        private val subgroupStorage = if (failMappingWrites) {
            object : SubconversationDAO by database.builder.subconversationDAO {
                override suspend fun upsert(
                    conversationId: QualifiedIDEntity,
                    subconversationId: String,
                    groupId: String,
                ) {
                    error("Storage unavailable")
                }
            }
        } else {
            database.builder.subconversationDAO
        }
        val subconversations = SubconversationRepositoryImpl(api, subgroupStorage)
        val decryptionGroups = mutableListOf<GroupID>()
        val scheduledProposals = mutableListOf<Pair<GroupID, Instant>>()
        var parentFallbackAttempted = false
        var proposalConsumed = false

        private val protoContentMapper = mock<ProtoContentMapper>()
        private val applicationMessageHandler = mock<ApplicationMessageHandler>(mode = MockMode.autoUnit)
        private val legalHoldHandler = mock<LegalHoldHandler>(mode = MockMode.autoUnit)
        private val staleEpochVerifier = mock<StaleEpochVerifier>()
        private val resetMLSConversation = mock<ResetMLSConversationUseCase>(mode = MockMode.autoUnit)
        val unpacker = MLSMessageUnpackerImpl(
            conversations,
            subconversations,
            mlsConversations,
            pendingProposals,
            TestUser.SELF.id,
            protoContentMapper,
        )
        val handler = NewMessageEventHandlerImpl(
            mock(),
            unpacker,
            applicationMessageHandler,
            legalHoldHandler,
            { _, _ -> },
            { _, _ -> },
            TestUser.SELF.id,
            staleEpochVerifier,
            resetMLSConversation,
        )

        init {
            everySuspend { conversations.getConversationProtocolInfo(PARENT_ID) } returns
                Either.Right(TestConversation.MLS_PROTOCOL_INFO.copy(groupId = PARENT_GROUP_ID))
            everySuspend { mlsConversations.decryptMessage(any(), any(), any()) } calls {
                val groupId = it.args[2] as GroupID
                decryptionGroups += groupId
                if (groupId == PARENT_GROUP_ID) {
                    parentFallbackAttempted = true
                    Either.Left(MLSFailure.InvalidGroupId)
                } else {
                    proposalConsumed = true
                    Either.Right(
                        listOf(
                            DecryptedMessageBundle.Proposal(
                                groupID = groupId,
                                commitDelay = COMMIT_DELAY,
                                identity = null,
                            )
                        )
                    )
                }
            }
            everySuspend { pendingProposals.scheduleCommit(any(), any()) } calls {
                scheduledProposals += (it.args[0] as GroupID) to (it.args[1] as Instant)
            }
            everySuspend { staleEpochVerifier.verifyEpoch(any(), any(), any(), any()) } returns
                Either.Left(MLSFailure.InvalidGroupId)
        }

        suspend fun remoteSubconversation(
            responseParentId: ConversationId,
            responseId: SubconversationId,
            groupId: GroupID,
        ) {
            everySuspend { api.fetchSubconversationDetails(PARENT_ID.toApi(), SUBCONVERSATION_ID.toApi()) } returns NetworkResponse.Success(
                SubconversationResponse(
                    id = responseId.toApi(),
                    parentId = responseParentId.toApi(),
                    groupId = groupId.value,
                    epoch = 2UL,
                    epochTimestamp = null,
                    mlsCipherSuiteTag = null,
                    members = emptyList(),
                ),
                emptyMap(),
                200,
            )
        }

        suspend fun remoteSubconversationFailure() {
            everySuspend { api.fetchSubconversationDetails(PARENT_ID.toApi(), SUBCONVERSATION_ID.toApi()) } returns
                NetworkResponse.Error(KaliumException.NoNetwork())
        }

        suspend fun remoteConversationNotFound() {
            everySuspend { api.fetchSubconversationDetails(PARENT_ID.toApi(), SUBCONVERSATION_ID.toApi()) } returns
                NetworkResponse.Error(
                    KaliumException.InvalidRequestError(
                        GenericAPIErrorResponse(404, "Conversation not found", "no-conversation")
                    )
                )
        }
    }

    private companion object {
        val PARENT_ID = TestConversation.ID
        val SUBCONVERSATION_ID = SubconversationId("conference")
        val PARENT_GROUP_ID = GroupID("parent-group")
        val SUBGROUP_ID = GroupID("subgroup")
        val EVENT_TIME = Instant.parse("2026-10-05T10:00:00Z")
        const val COMMIT_DELAY = 5L
    }
}
