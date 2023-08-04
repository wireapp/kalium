package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.SubconversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.receiver.conversation.message.MLSMessageUnpacker
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.base.authenticated.conversation.SubconversationDeleteRequest
import com.wire.kalium.network.api.base.authenticated.conversation.SubconversationResponse
import com.wire.kalium.network.api.base.model.ErrorResponse
import com.wire.kalium.network.api.base.model.QualifiedID
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.twice
import io.mockative.verify
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
                .withEstablishMLSGroupSuccessful()
                .arrange()

            joinSubconversationUseCase(Arrangement.CONVERSATION_ID, Arrangement.SUBCONVERSATION_ID).shouldSucceed()

            verify(arrangement.mlsConversationRepository)
                .suspendFunction(arrangement.mlsConversationRepository::establishMLSGroup)
                .with(eq(GroupID(Arrangement.SUBCONVERSATION_RESPONSE_WITH_ZERO_EPOCH.groupId)), anything())
                .wasInvoked(exactly = once)
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

            verify(arrangement.mlsConversationRepository)
                .suspendFunction(arrangement.mlsConversationRepository::joinGroupByExternalCommit)
                .with(eq(GroupID(Arrangement.SUBCONVERSATION_RESPONSE_WITH_NON_ZERO_EPOCH.groupId)), anything())
                .wasInvoked(exactly = once)
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

            verify(arrangement.subconversationRepository)
                .suspendFunction(arrangement.subconversationRepository::insertSubconversation)
                .with(
                    eq(Arrangement.CONVERSATION_ID),
                    eq(Arrangement.SUBCONVERSATION_ID,),
                    eq(GroupID(Arrangement.SUBCONVERSATION_RESPONSE_WITH_NON_ZERO_EPOCH.groupId))
                )
                .wasInvoked(exactly = once)
        }

    @Test
    fun givenStaleEpoch_whenInvokingUseCase_ThenDeleteAndEstablishGroup() =
        runTest {
            val (arrangement, joinSubconversationUseCase) = Arrangement()
                .withFetchingSubconversationDetails(Arrangement.SUBCONVERSATION_RESPONSE_WITH_STALE_EPOCH)
                .withDeleteSubconversationSuccessful()
                .withEstablishMLSGroupSuccessful()
                .arrange()

            joinSubconversationUseCase(Arrangement.CONVERSATION_ID, Arrangement.SUBCONVERSATION_ID).shouldSucceed()

            verify(arrangement.conversationApi)
                .suspendFunction(arrangement.conversationApi::deleteSubconversation)
                .with(
                    eq(Arrangement.SUBCONVERSATION_RESPONSE_WITH_STALE_EPOCH.parentId),
                    eq(Arrangement.SUBCONVERSATION_RESPONSE_WITH_STALE_EPOCH.id),
                    eq(
                        SubconversationDeleteRequest(
                            Arrangement.SUBCONVERSATION_RESPONSE_WITH_STALE_EPOCH.epoch,
                            Arrangement.SUBCONVERSATION_RESPONSE_WITH_STALE_EPOCH.groupId
                        )
                    )
                )
                .wasInvoked(exactly = once)

            verify(arrangement.mlsConversationRepository)
                .suspendFunction(arrangement.mlsConversationRepository::establishMLSGroup)
                .with(eq(GroupID(Arrangement.SUBCONVERSATION_RESPONSE_WITH_STALE_EPOCH.groupId)), eq(emptyList()))
                .wasInvoked(exactly = once)
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

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::joinGroupByExternalCommit)
            .with(eq(GroupID(Arrangement.SUBCONVERSATION_RESPONSE_WITH_NON_ZERO_EPOCH.groupId)), anything())
            .wasInvoked(twice)
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
        val conversationApi = mock(classOf<ConversationApi>())

        @Mock
        val mlsConversationRepository = mock(classOf<MLSConversationRepository>())

        @Mock
        val subconversationRepository = mock(classOf<SubconversationRepository>())

        @Mock
        val mlsMessageUnpacker = mock(classOf<MLSMessageUnpacker>())

        fun arrange() = this to JoinSubconversationUseCaseImpl(
            conversationApi,
            mlsConversationRepository,
            subconversationRepository,
            mlsMessageUnpacker
        )

        fun withEstablishMLSGroupSuccessful() = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::establishMLSGroup)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(Unit))
        }

        fun withJoinByExternalCommitSuccessful() = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::joinGroupByExternalCommit)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(null))
        }

        fun withJoinByExternalCommitGroupFailing(failure: CoreFailure, times: Int = Int.MAX_VALUE) = apply {
            var invocationCounter = 0
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::joinGroupByExternalCommit)
                .whenInvokedWith(matching { invocationCounter += 1; invocationCounter <= times }, anything())
                .thenReturn(Either.Left(failure))
        }

        fun withFetchingSubconversationGroupInfoSuccessful() = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::fetchSubconversationGroupInfo)
                .whenInvokedWith(anything(), anything())
                .thenReturn(NetworkResponse.Success(PUBLIC_GROUP_STATE, mapOf(), 200))
        }

        fun withFetchingSubconversationDetails(response: SubconversationResponse) = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::fetchSubconversationDetails)
                .whenInvokedWith(anything(), anything())
                .thenReturn(NetworkResponse.Success(response, emptyMap(), 200))
        }

        fun withDeleteSubconversationSuccessful() = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::deleteSubconversation)
                .whenInvokedWith(anything(), anything(), anything())
                .thenReturn(NetworkResponse.Success(Unit, emptyMap(), 200))
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
