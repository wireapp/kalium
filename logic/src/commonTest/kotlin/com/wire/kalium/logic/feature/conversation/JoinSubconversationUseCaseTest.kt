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
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.JoinSubconversationUseCaseImpl
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.SubconversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.authenticated.conversation.SubconversationDeleteRequest
import com.wire.kalium.network.api.authenticated.conversation.SubconversationResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.api.model.QualifiedID
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.matcher.any as mokkeryAny
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class JoinSubconversationUseCaseTest {

    @Test
    fun givenEpochIsZero_whenInvokingUseCase_ThenEstablishGroup() =
        runTest {
            val (arrangement, joinSubconversationUseCase) = Arrangement()
                .withFetchingSubconversationDetails(Arrangement.SUBCONVERSATION_RESPONSE_WITH_ZERO_EPOCH)
                .withEstablishMLSSubConversationGroupSuccessful()
                .arrange()

            joinSubconversationUseCase(Arrangement.CONVERSATION_ID, Arrangement.SUBCONVERSATION_ID).shouldSucceed()

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.mlsConversationRepository.establishMLSSubConversationGroup(
                    mokkeryAny(), GroupID(Arrangement.SUBCONVERSATION_RESPONSE_WITH_ZERO_EPOCH.groupId), mokkeryAny()
                )
            }
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

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.mlsConversationRepository.joinGroupByExternalCommit(
                    mokkeryAny(), GroupID(Arrangement.SUBCONVERSATION_RESPONSE_WITH_NON_ZERO_EPOCH.groupId), mokkeryAny()
                )
            }
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

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.subconversationRepository.insertSubconversation(
                    Arrangement.CONVERSATION_ID,
                    Arrangement.SUBCONVERSATION_ID,
                    GroupID(Arrangement.SUBCONVERSATION_RESPONSE_WITH_NON_ZERO_EPOCH.groupId)
                )
            }
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

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.conversationApi.deleteSubconversation(
                    Arrangement.SUBCONVERSATION_RESPONSE_WITH_STALE_EPOCH.parentId,
                    Arrangement.SUBCONVERSATION_RESPONSE_WITH_STALE_EPOCH.id,
                    SubconversationDeleteRequest(
                        Arrangement.SUBCONVERSATION_RESPONSE_WITH_STALE_EPOCH.epoch,
                        Arrangement.SUBCONVERSATION_RESPONSE_WITH_STALE_EPOCH.groupId
                    )
                )
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.mlsConversationRepository.establishMLSSubConversationGroup(
                    mokkeryAny(),
                    GroupID(Arrangement.SUBCONVERSATION_RESPONSE_WITH_STALE_EPOCH.groupId),
                    mokkeryAny(),
                )
            }
        }

    @Test
    fun givenStaleMessageFailure_whenInvokingUseCase_ThenRetryInSeparateTransaction() = runTest {
        val (arrangement, joinSubconversationUseCase) = Arrangement()
            .withFetchingSubconversationDetails(Arrangement.SUBCONVERSATION_RESPONSE_WITH_NON_ZERO_EPOCH)
            .withFetchingSubconversationGroupInfoSuccessful()
            .withJoinByExternalCommitGroupReturning(Either.Left(Arrangement.MLS_STALE_MESSAGE_FAILURE), Either.Right(Unit))
            .arrange()

        joinSubconversationUseCase(Arrangement.CONVERSATION_ID, Arrangement.SUBCONVERSATION_ID).shouldSucceed()

        verifySuspend(VerifyMode.exactly(2)) {
            arrangement.mlsConversationRepository.joinGroupByExternalCommit(
                mokkeryAny(),
                GroupID(Arrangement.SUBCONVERSATION_RESPONSE_WITH_NON_ZERO_EPOCH.groupId),
                mokkeryAny()
            )
        }

        verifySuspend(VerifyMode.exactly(2)) {
            arrangement.cryptoTransactionProvider.mlsTransaction<Either<CoreFailure, Unit>>("JoinSubconversation", mokkeryAny())
        }
    }

    @Test
    fun givenNonRecoverableFailure_whenInvokingUseCase_ThenFailureIsReported() = runTest {
        val (_, joinSubconversationUseCase) = Arrangement()
            .withFetchingSubconversationDetails(Arrangement.SUBCONVERSATION_RESPONSE_WITH_NON_ZERO_EPOCH)
            .withFetchingSubconversationGroupInfoSuccessful()
            .withJoinByExternalCommitGroupReturning(Either.Left(Arrangement.MLS_UNSUPPORTED_PROPOSAL_FAILURE))
            .arrange()

        joinSubconversationUseCase(Arrangement.CONVERSATION_ID, Arrangement.SUBCONVERSATION_ID).shouldFail()
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val conversationApi = mock<ConversationApi>()
        val mlsConversationRepository = mock<MLSConversationRepository>()
        val subconversationRepository = mock<SubconversationRepository>(mode = MockMode.autoUnit)

        suspend fun arrange() = this to JoinSubconversationUseCaseImpl(
            conversationApi,
            mlsConversationRepository,
            subconversationRepository,
            cryptoTransactionProvider
        ).also {
            withMLSTransactionReturning(Either.Right(Unit))
        }

        suspend fun withEstablishMLSSubConversationGroupSuccessful() = apply {
            everySuspend {
                mlsConversationRepository.establishMLSSubConversationGroup(mokkeryAny(), mokkeryAny(), mokkeryAny())
            } returns Either.Right(Unit)
        }

        suspend fun withJoinByExternalCommitSuccessful() = apply {
            everySuspend {
                mlsConversationRepository.joinGroupByExternalCommit(mokkeryAny(), mokkeryAny(), mokkeryAny())
            } returns Either.Right(Unit)
        }

        suspend fun withJoinByExternalCommitGroupReturning(vararg result: Either<CoreFailure, Unit>) = apply {
            var callIndex = 0
            everySuspend {
                mlsConversationRepository.joinGroupByExternalCommit(mokkeryAny(), mokkeryAny(), mokkeryAny())
            } calls {
                result[callIndex++]
            }
        }

        suspend fun withFetchingSubconversationGroupInfoSuccessful() = apply {
            everySuspend {
                conversationApi.fetchSubconversationGroupInfo(CONVERSATION_ID.toApi(), SUBCONVERSATION_ID.toApi())
            } returns NetworkResponse.Success(PUBLIC_GROUP_STATE, mapOf(), 200)
        }

        suspend fun withFetchingSubconversationDetails(response: SubconversationResponse) = apply {
            everySuspend {
                conversationApi.fetchSubconversationDetails(CONVERSATION_ID.toApi(), SUBCONVERSATION_ID.toApi())
            } returns NetworkResponse.Success(response, emptyMap(), 200)
        }

        suspend fun withDeleteSubconversationSuccessful() = apply {
            everySuspend {
                conversationApi.deleteSubconversation(
                    SUBCONVERSATION_RESPONSE_WITH_STALE_EPOCH.parentId,
                    SUBCONVERSATION_RESPONSE_WITH_STALE_EPOCH.id,
                    SubconversationDeleteRequest(
                        SUBCONVERSATION_RESPONSE_WITH_STALE_EPOCH.epoch,
                        SUBCONVERSATION_RESPONSE_WITH_STALE_EPOCH.groupId
                    )
                )
            } returns NetworkResponse.Success(Unit, emptyMap(), 200)
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
            val MLS_STALE_MESSAGE_FAILURE = MLSFailure.MessageRejected(
                NetworkFailure.MlsMessageRejectedFailure.StaleMessage
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
