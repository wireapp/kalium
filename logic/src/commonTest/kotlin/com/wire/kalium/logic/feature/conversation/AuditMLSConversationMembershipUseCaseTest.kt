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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.util.DateTimeUtil
import dev.mokkery.MockMode
import dev.mokkery.answering.sequentiallyReturns
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class AuditMLSConversationMembershipUseCaseTest {

    @Test
    fun givenExistingAndMissingGroups_whenAuditing_thenOnlyMissingGroupIsJoined() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withConversations(listOf(MLS_CONVERSATION, MIXED_CONVERSATION))
            .withGroupExists(MLS_GROUP_ID, exists = true)
            .withGroupCheckResults(MIXED_GROUP_ID, Either.Right(false), Either.Right(true))
            .withConversationById(MIXED_CONVERSATION)
            .withJoinSuccessful()
            .arrange()

        val result = useCase(arrangement.transactionContext)

        verifySuspend(VerifyMode.not) {
            arrangement.joinExistingMLSConversationUseCase.invoke(
                any(),
                eq(MLS_CONVERSATION.id),
                any(),
                any()
            )
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.joinExistingMLSConversationUseCase.invoke(
                any(),
                eq(MIXED_CONVERSATION.id),
                any(),
                eq(true)
            )
        }
        assertIs<AuditMLSConversationMembershipResult.Success>(result)
    }

    @Test
    fun givenOneGroupCheckFails_whenAuditing_thenRemainingGroupsAreAttemptedAndResultFails() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withConversations(listOf(MLS_CONVERSATION, MIXED_CONVERSATION))
            .withGroupCheckFailed(MLS_GROUP_ID)
            .withGroupCheckResults(MIXED_GROUP_ID, Either.Right(false), Either.Right(true))
            .withConversationById(MIXED_CONVERSATION)
            .withJoinSuccessful()
            .arrange()

        val result = useCase(arrangement.transactionContext)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.joinExistingMLSConversationUseCase.invoke(
                any(),
                eq(MIXED_CONVERSATION.id),
                any(),
                eq(true)
            )
        }
        assertIs<AuditMLSConversationMembershipResult.Failure>(result)
    }

    @Test
    fun givenJoinSucceedsButGroupIsStillMissing_whenAuditing_thenFailureIsReturned() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withConversations(listOf(MIXED_CONVERSATION))
            .withGroupCheckResults(MIXED_GROUP_ID, Either.Right(false), Either.Right(false))
            .withConversationById(MIXED_CONVERSATION)
            .withJoinSuccessful()
            .arrange()

        val result = useCase(arrangement.transactionContext)

        val failure = assertIs<AuditMLSConversationMembershipResult.Failure>(result)
        assertIs<MLSFailure.ConversationNotFound>(failure.failure)
    }

    @Test
    fun givenJoinSucceedsButPostJoinGroupCheckFails_whenAuditing_thenFailureIsReturned() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withConversations(listOf(MIXED_CONVERSATION))
            .withGroupCheckResults(
                MIXED_GROUP_ID,
                Either.Right(false),
                Either.Left(MLSFailure.ConversationNotFound)
            )
            .withConversationById(MIXED_CONVERSATION)
            .withJoinSuccessful()
            .arrange()

        val result = useCase(arrangement.transactionContext)

        val failure = assertIs<AuditMLSConversationMembershipResult.Failure>(result)
        assertIs<MLSFailure.ConversationNotFound>(failure.failure)
    }

    @Test
    fun givenGroupIdChangesDuringJoin_whenAuditing_thenRefreshedGroupIsVerified() = runTest {
        val refreshedConversation = MIXED_CONVERSATION.copy(
            protocol = (MIXED_CONVERSATION.protocol as Conversation.ProtocolInfo.Mixed).copy(groupId = REFRESHED_GROUP_ID)
        )
        val (arrangement, useCase) = Arrangement()
            .withConversations(listOf(MIXED_CONVERSATION))
            .withGroupExists(MIXED_GROUP_ID, exists = false)
            .withGroupExists(REFRESHED_GROUP_ID, exists = true)
            .withConversationById(refreshedConversation)
            .withJoinSuccessful()
            .arrange()

        val result = useCase(arrangement.transactionContext)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.hasEstablishedMLSGroup(any(), eq(MIXED_GROUP_ID))
            arrangement.mlsConversationRepository.hasEstablishedMLSGroup(any(), eq(REFRESHED_GROUP_ID))
        }
        assertIs<AuditMLSConversationMembershipResult.Success>(result)
    }

    @Test
    fun givenConversationReloadFailsAfterJoin_whenAuditing_thenFailureIsReturned() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withConversations(listOf(MIXED_CONVERSATION))
            .withGroupExists(MIXED_GROUP_ID, exists = false)
            .withConversationByIdFailed(MIXED_CONVERSATION.id)
            .withJoinSuccessful()
            .arrange()

        val result = useCase(arrangement.transactionContext)

        val failure = assertIs<AuditMLSConversationMembershipResult.Failure>(result)
        assertIs<StorageFailure.DataNotFound>(failure.failure)
    }

    @Test
    fun givenConversationIsNoLongerMLSCapableAfterJoin_whenAuditing_thenFailureIsReturned() = runTest {
        val refreshedConversation = MIXED_CONVERSATION.copy(protocol = Conversation.ProtocolInfo.Proteus)
        val (arrangement, useCase) = Arrangement()
            .withConversations(listOf(MIXED_CONVERSATION))
            .withGroupExists(MIXED_GROUP_ID, exists = false)
            .withConversationById(refreshedConversation)
            .withJoinSuccessful()
            .arrange()

        val result = useCase(arrangement.transactionContext)

        val failure = assertIs<AuditMLSConversationMembershipResult.Failure>(result)
        val unknownFailure = assertIs<CoreFailure.Unknown>(failure.failure)
        assertIs<IllegalStateException>(unknownFailure.rootCause)
    }

    @Test
    fun givenPostJoinVerificationFails_whenAuditing_thenRemainingConversationsAreAttempted() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withConversations(listOf(MLS_CONVERSATION, MIXED_CONVERSATION))
            .withGroupCheckResults(MLS_GROUP_ID, Either.Right(false), Either.Right(false))
            .withGroupCheckResults(MIXED_GROUP_ID, Either.Right(false), Either.Right(true))
            .withConversationById(MLS_CONVERSATION)
            .withConversationById(MIXED_CONVERSATION)
            .withJoinSuccessful()
            .arrange()

        val result = useCase(arrangement.transactionContext)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.joinExistingMLSConversationUseCase.invoke(any(), eq(MLS_CONVERSATION.id), any(), eq(true))
            arrangement.joinExistingMLSConversationUseCase.invoke(any(), eq(MIXED_CONVERSATION.id), any(), eq(true))
        }
        assertIs<AuditMLSConversationMembershipResult.Failure>(result)
    }

    @Test
    fun givenConversationQueryFails_whenAuditing_thenFailureIsReturned() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withConversationQueryFailed()
            .arrange()

        val result = useCase(arrangement.transactionContext)

        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.hasEstablishedMLSGroup(any(), any())
        }
        assertIs<AuditMLSConversationMembershipResult.Failure>(result)
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val conversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)
        val mlsConversationRepository = mock<MLSConversationRepository>(mode = MockMode.autoUnit)
        val joinExistingMLSConversationUseCase = mock<JoinExistingMLSConversationUseCase>(mode = MockMode.autoUnit)

        suspend fun withConversations(conversations: List<Conversation>) = apply {
            everySuspend {
                conversationRepository.getActiveMLSConversationsForMembershipAudit()
            } returns Either.Right(conversations)
        }

        suspend fun withConversationQueryFailed() = apply {
            everySuspend {
                conversationRepository.getActiveMLSConversationsForMembershipAudit()
            } returns Either.Left(StorageFailure.DataNotFound)
        }

        suspend fun withGroupExists(groupID: GroupID, exists: Boolean) = apply {
            everySuspend {
                mlsConversationRepository.hasEstablishedMLSGroup(any(), eq(groupID))
            } returns Either.Right(exists)
        }

        suspend fun withGroupCheckFailed(groupID: GroupID) = apply {
            everySuspend {
                mlsConversationRepository.hasEstablishedMLSGroup(any(), eq(groupID))
            } returns Either.Left(MLSFailure.ConversationNotFound)
        }

        suspend fun withGroupCheckResults(groupID: GroupID, vararg results: Either<MLSFailure, Boolean>) = apply {
            everySuspend {
                mlsConversationRepository.hasEstablishedMLSGroup(any(), eq(groupID))
            } sequentiallyReturns results.toList()
        }

        suspend fun withConversationById(conversation: Conversation) = apply {
            everySuspend {
                conversationRepository.getNonDeletedConversationById(eq(conversation.id))
            } returns Either.Right(conversation)
        }

        suspend fun withConversationByIdFailed(conversationId: ConversationId) = apply {
            everySuspend {
                conversationRepository.getNonDeletedConversationById(eq(conversationId))
            } returns Either.Left(StorageFailure.DataNotFound)
        }

        suspend fun withJoinSuccessful() = apply {
            everySuspend {
                joinExistingMLSConversationUseCase.invoke(any(), any(), any(), any())
            } returns Either.Right(Unit)
        }

        fun arrange() = this to AuditMLSConversationMembershipUseCaseImpl(
            conversationRepository,
            mlsConversationRepository,
            joinExistingMLSConversationUseCase
        )
    }

    private companion object {
        val MLS_GROUP_ID = GroupID("mls-group")
        val MIXED_GROUP_ID = GroupID("mixed-group")
        val REFRESHED_GROUP_ID = GroupID("refreshed-group")
        val MLS_CONVERSATION = TestConversation.GROUP(
            protocolInfo = Conversation.ProtocolInfo.MLS(
                groupId = MLS_GROUP_ID,
                groupState = Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED,
                epoch = 1UL,
                keyingMaterialLastUpdate = DateTimeUtil.currentInstant(),
                cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
            )
        ).copy(id = ConversationId("mls", "domain"))
        val MIXED_CONVERSATION = TestConversation.GROUP(
            protocolInfo = Conversation.ProtocolInfo.Mixed(
                groupId = MIXED_GROUP_ID,
                groupState = Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_WELCOME_MESSAGE,
                epoch = 1UL,
                keyingMaterialLastUpdate = DateTimeUtil.currentInstant(),
                cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
            )
        ).copy(id = ConversationId("mixed", "domain"))
    }
}
