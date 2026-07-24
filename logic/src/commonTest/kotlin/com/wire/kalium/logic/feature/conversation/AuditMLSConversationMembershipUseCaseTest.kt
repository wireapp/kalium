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
            .withGroupExists(MIXED_GROUP_ID, exists = false)
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
            .withGroupExists(MIXED_GROUP_ID, exists = false)
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
