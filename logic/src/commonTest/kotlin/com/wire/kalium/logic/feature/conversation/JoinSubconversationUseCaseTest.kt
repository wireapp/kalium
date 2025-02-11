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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.conversation.JoinSubconversationUseCaseImpl
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.SubconversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.sync.receiver.conversation.message.MLSMessageUnpacker
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.authenticated.conversation.SubconversationDeleteRequest
import com.wire.kalium.network.api.authenticated.conversation.SubconversationResponse
import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.api.model.QualifiedID
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import io.mockative.twice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@OptIn(ExperimentalCoroutinesApi::class)
class JoinSubconversationUseCaseTest {

    @Test
    fun givenEpochIsZero_whenInvokingUseCase_ThenEstablishGroup() =
        runTest {
            val (arrangement, joinSubconversationUseCase) = Arrangement()
                .withFetchingSubconversationDetails(Arrangement.SUBCONVERSATION_RESPONSE_WITH_ZERO_EPOCH)
                .withEstablishMLSSubConversationGroupSuccessful()
                .arrange()

            joinSubconversationUseCase(Arrangement.CONVERSATION_ID, Arrangement.SUBCONVERSATION_ID).shouldSucceed()

            coVerify {
                arrangement.mlsConversationRepository.establishMLSSubConversationGroup(
                    groupID = eq(GroupID(Arrangement.SUBCONVERSATION_RESPONSE_WITH_ZERO_EPOCH.groupId)),
                    parentId = any()
                )
            }.wasInvoked(exactly = once)
        }

    @Test
    fun givenEpochIsNonZero_whenInvokingUseCase_ThenJoinExistingGroup() =
        runTest {
            val (arrangement, joinSubconversationUseCase) = Arrangement()
                .withFetchingSubconversationDetails(Arrangement.SUBCONVERSATION_RESPONSE_WITH_NON_ZERO_EPOCH)
                .withFetchingSubconversationGroupInfoSuccessful()
                .withJoinByExternalCommitSuccessful()
                .arrange()

            joinSubconversationUseCase(Arrangement.CONVERSATION_ID, Arrangement.SUBCONVERSATION_ID).shouldSucceed()

            coVerify {
                arrangement.mlsConversationRepository.joinGroupByExternalCommit(
                    groupID = eq(GroupID(Arrangement.SUBCONVERSATION_RESPONSE_WITH_NON_ZERO_EPOCH.groupId)),
                    groupInfo = any()
                )
            }.wasInvoked(exactly = once)
        }

    @Test
    fun givenJoiningSubconversation_whenInvokingUseCase_ThenSubconversationIsPersisted() =
        runTest {
            val (arrangement, joinSubconversationUseCase) = Arrangement()
                .withFetchingSubconversationDetails(Arrangement.SUBCONVERSATION_RESPONSE_WITH_NON_ZERO_EPOCH)
                .withFetchingSubconversationGroupInfoSuccessful()
                .withJoinByExternalCommitSuccessful()
                .arrange()

            joinSubconversationUseCase(Arrangement.CONVERSATION_ID, Arrangement.SUBCONVERSATION_ID).shouldSucceed()

            coVerify {
                arrangement.subconversationRepository.insertSubconversation(
                    eq(Arrangement.CONVERSATION_ID),
                    eq(Arrangement.SUBCONVERSATION_ID),
                    eq(GroupID(Arrangement.SUBCONVERSATION_RESPONSE_WITH_NON_ZERO_EPOCH.groupId))
                )
            }.wasInvoked(exactly = once)
        }

    @Test
    fun givenStaleEpoch_whenInvokingUseCase_ThenDeleteAndEstablishGroup() =
        runTest {
            val (arrangement, joinSubconversationUseCase) = Arrangement()
                .withFetchingSubconversationDetails(Arrangement.SUBCONVERSATION_RESPONSE_WITH_STALE_EPOCH)
                .withDeleteSubconversationSuccessful()
                .withEstablishMLSSubConversationGroupSuccessful()
                .arrange()

            joinSubconversationUseCase(Arrangement.CONVERSATION_ID, Arrangement.SUBCONVERSATION_ID).shouldSucceed()

            coVerify {
                arrangement.conversationApi.deleteSubconversation(
                    eq(Arrangement.SUBCONVERSATION_RESPONSE_WITH_STALE_EPOCH.parentId),
                    eq(Arrangement.SUBCONVERSATION_RESPONSE_WITH_STALE_EPOCH.id),
                    eq(
                        SubconversationDeleteRequest(
                            Arrangement.SUBCONVERSATION_RESPONSE_WITH_STALE_EPOCH.epoch,
                            Arrangement.SUBCONVERSATION_RESPONSE_WITH_STALE_EPOCH.groupId
                        )
                    )
                )
            }.wasInvoked(exactly = once)

            coVerify {
                arrangement.mlsConversationRepository.establishMLSSubConversationGroup(
                    eq(GroupID(Arrangement.SUBCONVERSATION_RESPONSE_WITH_STALE_EPOCH.groupId)),
                    any()
                )
            }.wasInvoked(exactly = once)
        }

    @Test
    fun givenStaleMessageFailure_whenInvokingUseCase_ThenRetry() = runTest {
        val (arrangement, joinSubconversationUseCase) = Arrangement()
            .withFetchingSubconversationDetails(Arrangement.SUBCONVERSATION_RESPONSE_WITH_NON_ZERO_EPOCH)
            .withFetchingSubconversationGroupInfoSuccessful()
            .withJoinByExternalCommitSuccessful()
            .withJoinByExternalCommitGroupFailing(Arrangement.MLS_STALE_MESSAGE_FAILURE, times = 1)
            .arrange()

        joinSubconversationUseCase(Arrangement.CONVERSATION_ID, Arrangement.SUBCONVERSATION_ID).shouldSucceed()

        coVerify {
            arrangement.mlsConversationRepository.joinGroupByExternalCommit(
                eq(GroupID(Arrangement.SUBCONVERSATION_RESPONSE_WITH_NON_ZERO_EPOCH.groupId)),
                any()
            )
        }.wasInvoked(twice)
    }

    @Test
    fun givenNonRecoverableFailure_whenInvokingUseCase_ThenFailureIsReported() = runTest {
        val (_, joinSubconversationUseCase) = Arrangement()
            .withFetchingSubconversationDetails(Arrangement.SUBCONVERSATION_RESPONSE_WITH_NON_ZERO_EPOCH)
            .withFetchingSubconversationGroupInfoSuccessful()
            .withJoinByExternalCommitGroupFailing(Arrangement.MLS_UNSUPPORTED_PROPOSAL_FAILURE)
            .arrange()

        joinSubconversationUseCase(Arrangement.CONVERSATION_ID, Arrangement.SUBCONVERSATION_ID).shouldFail()
    }

    private class Arrangement {

        @Mock
        val conversationApi = mock(ConversationApi::class)

        @Mock
        val mlsConversationRepository = mock(MLSConversationRepository::class)

        @Mock
        val subconversationRepository = mock(SubconversationRepository::class)

        @Mock
        val mlsMessageUnpacker = mock(MLSMessageUnpacker::class)

        fun arrange() = this to JoinSubconversationUseCaseImpl(
            conversationApi,
            mlsConversationRepository,
            subconversationRepository,
            mlsMessageUnpacker
        )

        suspend fun withEstablishMLSSubConversationGroupSuccessful() = apply {
            coEvery {
                mlsConversationRepository.establishMLSSubConversationGroup(any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withJoinByExternalCommitSuccessful() = apply {
            coEvery {
                mlsConversationRepository.joinGroupByExternalCommit(any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withJoinByExternalCommitGroupFailing(failure: CoreFailure, times: Int = Int.MAX_VALUE) = apply {
            var invocationCounter = 0
            coEvery {
                mlsConversationRepository.joinGroupByExternalCommit(matches { invocationCounter += 1; invocationCounter <= times }, any())
            }.returns(Either.Left(failure))
        }

        suspend fun withFetchingSubconversationGroupInfoSuccessful() = apply {
            coEvery {
                conversationApi.fetchSubconversationGroupInfo(any(), any())
            }.returns(NetworkResponse.Success(PUBLIC_GROUP_STATE, mapOf(), 200))
        }

        suspend fun withFetchingSubconversationDetails(response: SubconversationResponse) = apply {
            coEvery {
                conversationApi.fetchSubconversationDetails(any(), any())
            }.returns(NetworkResponse.Success(response, emptyMap(), 200))
        }

        suspend fun withDeleteSubconversationSuccessful() = apply {
            coEvery {
                conversationApi.deleteSubconversation(any(), any(), any())
            }.returns(NetworkResponse.Success(Unit, emptyMap(), 200))
        }

        companion object {
            val PUBLIC_GROUP_STATE = "public_group_state".encodeToByteArray()
            val MLS_UNSUPPORTED_PROPOSAL_FAILURE = NetworkFailure.ServerMiscommunication(
                KaliumException.InvalidRequestError(
                    ErrorResponse(
                        422,
                        "Unsupported proposal type",
                        "mls-unsupported-proposal"
                    )
                )
            )
            val MLS_STALE_MESSAGE_FAILURE = NetworkFailure.ServerMiscommunication(
                KaliumException.InvalidRequestError(
                    ErrorResponse(
                        403,
                        "The conversation epoch in a message is too old",
                        "mls-stale-message"
                    )
                )
            )
            val CONVERSATION_ID = ConversationId("id1", "domain")
            val SUBCONVERSATION_ID = SubconversationId("subconversation_id")
            val SUBCONVERSATION_RESPONSE_WITH_ZERO_EPOCH = SubconversationResponse(
                SUBCONVERSATION_ID.toApi(),
                QualifiedID(CONVERSATION_ID.value, CONVERSATION_ID.domain),
                "groupid",
                0UL,
                null,
                0,
                emptyList()
            )
            val SUBCONVERSATION_RESPONSE_WITH_NON_ZERO_EPOCH = SUBCONVERSATION_RESPONSE_WITH_ZERO_EPOCH.copy(
                epoch = 3UL,
                epochTimestamp = Clock.System.now().toIsoDateTimeString()
            )
            val SUBCONVERSATION_RESPONSE_WITH_STALE_EPOCH = SUBCONVERSATION_RESPONSE_WITH_ZERO_EPOCH.copy(
                epoch = 3UL,
                epochTimestamp = Clock.System.now().minus(25.toDuration(DurationUnit.HOURS)).toIsoDateTimeString()
            )
        }
    }
}
