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
package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.LeaveSubconversationUseCaseImpl
import com.wire.kalium.logic.data.conversation.SubconversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.message.PendingSubconversationProposal
import com.wire.kalium.logic.data.message.PendingSubconversationProposals
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.authenticated.conversation.SubconversationResponse
import com.wire.kalium.network.api.authenticated.conversation.SubconversationMemberDTO
import com.wire.kalium.network.api.model.QualifiedID
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.time.Instant

class LeaveSubconversationUseCaseTest {

    @Test
    fun givenMemberOfSubconversation_whenInvokingUseCase_thenMakeApiCallToRemoveSelf() = runTest {
        val (arrangement, leaveSubconversation) = Arrangement()
            .withGetSubconversationInfoReturns(Arrangement.SUBCONVERSATION_GROUP_ID)
            .withLeaveSubconversationSuccessful()
            .withWipeMlsConversationSuccessful()
            .withDeleteSubconversationSuccessful()
            .arrange()

        leaveSubconversation(arrangement.mlsContext, Arrangement.CONVERSATION_ID, Arrangement.SUBCONVERSATION_ID)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationApi.leaveSubconversation(
                Arrangement.CONVERSATION_ID.toApi(),
                Arrangement.SUBCONVERSATION_ID.toApi()
            )
        }
    }

    @Test
    fun givenNotMemberOfSubconversation_whenInvokingUseCase_thenNoApiCallToRemoveSelfIsMade() = runTest {
        val (arrangement, leaveSubconversation) = Arrangement()
            .withGetSubconversationInfoReturns(null)
            .withFetchingSubconversationDetails(Arrangement.SUBCONVERSATION_RESPONSE_WITH_ZERO_EPOCH)
            .arrange()

        leaveSubconversation(arrangement.mlsContext, Arrangement.CONVERSATION_ID, Arrangement.SUBCONVERSATION_ID)

        verifySuspend(VerifyMode.not) {
            arrangement.conversationApi.leaveSubconversation(
                Arrangement.CONVERSATION_ID.toApi(),
                Arrangement.SUBCONVERSATION_ID.toApi()
            )
        }
    }

    @Test
    fun givenMembershipIsNotKnown_whenInvokingUseCase_thenQueryMembershipFromApi() = runTest {
        val (arrangement, leaveSubconversation) = Arrangement()
            .withGetSubconversationInfoReturns(null)
            .withFetchingSubconversationDetails(Arrangement.SUBCONVERSATION_RESPONSE_WITH_ZERO_EPOCH)
            .arrange()

        leaveSubconversation(arrangement.mlsContext, Arrangement.CONVERSATION_ID, Arrangement.SUBCONVERSATION_ID)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationApi.fetchSubconversationDetails(
                Arrangement.CONVERSATION_ID.toApi(),
                Arrangement.SUBCONVERSATION_ID.toApi()
            )
        }
    }

    @Test
    fun givenApiCallToRemoveSelfIsSuccessful_whenInvokingUseCase_thenWipeLocalMlsGroup() = runTest {
        val (arrangement, leaveSubconversation) = Arrangement()
            .withGetSubconversationInfoReturns(Arrangement.SUBCONVERSATION_GROUP_ID)
            .withLeaveSubconversationSuccessful()
            .withWipeMlsConversationSuccessful()
            .withDeleteSubconversationSuccessful()
            .arrange()

        leaveSubconversation(arrangement.mlsContext, Arrangement.CONVERSATION_ID, Arrangement.SUBCONVERSATION_ID)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsContext.wipeConversation(Arrangement.SUBCONVERSATION_GROUP_ID.toCrypto())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.subconversationRepository.deleteSubconversation(Arrangement.CONVERSATION_ID, Arrangement.SUBCONVERSATION_ID)
        }

        verifySuspend(VerifyMode.not) {
            arrangement.subgroupProposals.markForRetirement(any())
        }
    }

    @Test
    fun givenPendingProposal_whenLeaveSucceeds_thenPersistRetirementAfterBackendLeaveBeforeLocalCleanup() = runTest {
        val pendingProposal = PendingSubconversationProposal(
            Arrangement.SUBCONVERSATION_GROUP_ID,
            Instant.parse("2020-01-01T00:00:00Z"),
            "proposal-token",
        )
        val calls = mutableListOf<String>()
        val (arrangement, leaveSubconversation) = Arrangement()
            .withGetSubconversationInfoReturns(Arrangement.SUBCONVERSATION_GROUP_ID)
            .withPendingProposal(
                pendingProposal,
                onCurrent = { calls += "snapshot" },
                onMark = { calls += "mark" },
            )
            .withLeaveSubconversationSuccessful { calls += "leave" }
            .withWipeMlsConversationSuccessful { calls += "wipe" }
            .withDeleteSubconversationSuccessful { calls += "delete" }
            .arrange()

        leaveSubconversation(arrangement.mlsContext, Arrangement.CONVERSATION_ID, Arrangement.SUBCONVERSATION_ID)
            .shouldSucceed { assertEquals(pendingProposal.copy(leaveRequested = true), it) }

        assertEquals(listOf("snapshot", "leave", "mark", "delete", "wipe"), calls)
    }

    @Test
    fun givenRemoteMembershipAndPendingProposal_whenLeaveSucceeds_thenReturnSnapshottedToken() = runTest {
        val pendingProposal = PendingSubconversationProposal(
            Arrangement.SUBCONVERSATION_GROUP_ID,
            Instant.parse("2020-01-01T00:00:00Z"),
            "remote-proposal-token",
        )
        val (arrangement, leaveSubconversation) = Arrangement()
            .withGetSubconversationInfoReturns(null)
            .withFetchingSubconversationDetails(Arrangement.SUBCONVERSATION_RESPONSE_WITH_SELF)
            .withPendingProposal(pendingProposal)
            .withLeaveSubconversationSuccessful()
            .withWipeMlsConversationSuccessful()
            .withDeleteSubconversationSuccessful()
            .arrange()

        leaveSubconversation(arrangement.mlsContext, Arrangement.CONVERSATION_ID, Arrangement.SUBCONVERSATION_ID)
            .shouldSucceed { assertEquals(pendingProposal.copy(leaveRequested = true), it) }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.subgroupProposals.current(Arrangement.SUBCONVERSATION_GROUP_ID)
        }
    }

    @Test
    fun givenBackendLeaveFails_thenRetirementIsNotMarkedAndLocalStateIsPreserved() = runTest {
        val pendingProposal = PendingSubconversationProposal(
            Arrangement.SUBCONVERSATION_GROUP_ID,
            Instant.parse("2020-01-01T00:00:00Z"),
            "proposal-token",
        )
        val (arrangement, leaveSubconversation) = Arrangement()
            .withGetSubconversationInfoReturns(Arrangement.SUBCONVERSATION_GROUP_ID)
            .withPendingProposal(pendingProposal)
            .withLeaveSubconversationFailure()
            .arrange()

        leaveSubconversation(arrangement.mlsContext, Arrangement.CONVERSATION_ID, Arrangement.SUBCONVERSATION_ID).shouldFail()

        verifySuspend(VerifyMode.not) { arrangement.subgroupProposals.markForRetirement(any()) }
        verifySuspend(VerifyMode.not) { arrangement.subconversationRepository.deleteSubconversation(any(), any()) }
        verifySuspend(VerifyMode.not) { arrangement.mlsContext.wipeConversation(any()) }
    }

    @Test
    fun givenRetirementMetadataWriteFails_thenLocalStateIsPreserved() = runTest {
        val pendingProposal = PendingSubconversationProposal(
            Arrangement.SUBCONVERSATION_GROUP_ID,
            Instant.parse("2020-01-01T00:00:00Z"),
            "proposal-token",
        )
        val (arrangement, leaveSubconversation) = Arrangement()
            .withGetSubconversationInfoReturns(Arrangement.SUBCONVERSATION_GROUP_ID)
            .withPendingProposalSnapshot(pendingProposal)
            .withLeaveSubconversationSuccessful()
            .withRetirementMarkingFailure(pendingProposal)
            .arrange()

        assertFails {
            leaveSubconversation(arrangement.mlsContext, Arrangement.CONVERSATION_ID, Arrangement.SUBCONVERSATION_ID)
        }

        verifySuspend(VerifyMode.not) { arrangement.subconversationRepository.deleteSubconversation(any(), any()) }
        verifySuspend(VerifyMode.not) { arrangement.mlsContext.wipeConversation(any()) }
    }

    private class Arrangement: CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val conversationApi = mock<ConversationApi>(mode = MockMode.autoUnit)
        val subconversationRepository = mock<SubconversationRepository>(mode = MockMode.autoUnit)
        val selfClientIdProvider = mock<CurrentClientIdProvider>(mode = MockMode.autoUnit)
        val subgroupProposals = mock<PendingSubconversationProposals>(mode = MockMode.autoUnit)

        init {
            everySuspend { subgroupProposals.current(any()) } returns null
            everySuspend { subgroupProposals.markForRetirement(any()) } returns null
        }

        suspend fun arrange() = this to LeaveSubconversationUseCaseImpl(
            conversationApi,
            subconversationRepository,
            TestUser.SELF.id,
            selfClientIdProvider,
            subgroupProposals,
        ).also {
            everySuspend {
                selfClientIdProvider.invoke()
            } returns Either.Right(TestClient.CLIENT_ID)
        }

        suspend fun withFetchingSubconversationDetails(response: SubconversationResponse) = apply {
            everySuspend {
                conversationApi.fetchSubconversationDetails(any(), any())
            } returns NetworkResponse.Success(response, emptyMap(), 200)
        }

        suspend fun withLeaveSubconversationSuccessful(onCall: () -> Unit = {}) = apply {
            everySuspend {
                conversationApi.leaveSubconversation(any(), any())
            } calls {
                onCall()
                NetworkResponse.Success(Unit, emptyMap(), 200)
            }
        }

        suspend fun withLeaveSubconversationFailure() = apply {
            everySuspend {
                conversationApi.leaveSubconversation(any(), any())
            } returns NetworkResponse.Error(KaliumException.NoNetwork())
        }

        suspend fun withPendingProposal(
            pendingProposal: PendingSubconversationProposal,
            onCurrent: () -> Unit = {},
            onMark: () -> Unit = {},
        ) = apply {
            withPendingProposalSnapshot(pendingProposal, onCurrent)
            everySuspend {
                subgroupProposals.markForRetirement(pendingProposal)
            } calls {
                onMark()
                pendingProposal.copy(leaveRequested = true)
            }
        }

        suspend fun withPendingProposalSnapshot(
            pendingProposal: PendingSubconversationProposal,
            onCall: () -> Unit = {},
        ) = apply {
            everySuspend {
                subgroupProposals.current(SUBCONVERSATION_GROUP_ID)
            } calls {
                onCall()
                pendingProposal
            }
        }

        suspend fun withRetirementMarkingFailure(pendingProposal: PendingSubconversationProposal) = apply {
            everySuspend {
                subgroupProposals.markForRetirement(pendingProposal)
            } throws IllegalStateException("Metadata unavailable")
        }

        suspend fun withGetSubconversationInfoReturns(groupID: GroupID?) = apply {
            everySuspend {
                subconversationRepository.getSubconversationInfo(any(), any())
            } returns groupID
        }

        suspend fun withDeleteSubconversationSuccessful(onCall: () -> Unit = {}) = apply {
            everySuspend {
                subconversationRepository.deleteSubconversation(any(), any())
            } calls {
                onCall()
                Unit
            }
        }

        suspend fun withWipeMlsConversationSuccessful(onCall: () -> Unit = {}) = apply {
            everySuspend {
                mlsContext.wipeConversation(any())
            } calls {
                onCall()
                Unit
            }
        }

        companion object {
            val SUBCONVERSATION_GROUP_ID = GroupID("group_id")
            val CONVERSATION_ID = ConversationId("id1", "domain")
            val SUBCONVERSATION_ID = SubconversationId("subconversation_id")
            val SUBCONVERSATION_RESPONSE_WITH_ZERO_EPOCH = SubconversationResponse(
                SUBCONVERSATION_ID.toApi(),
                QualifiedID(CONVERSATION_ID.value, CONVERSATION_ID.domain),
                SUBCONVERSATION_GROUP_ID.value,
                0UL,
                null,
                0,
                emptyList()
            )
            val SUBCONVERSATION_RESPONSE_WITH_SELF = SUBCONVERSATION_RESPONSE_WITH_ZERO_EPOCH.copy(
                members = listOf(SubconversationMemberDTO(TestClient.CLIENT_ID.value, TestUser.SELF.id.value, TestUser.SELF.id.domain))
            )
        }
    }
}
