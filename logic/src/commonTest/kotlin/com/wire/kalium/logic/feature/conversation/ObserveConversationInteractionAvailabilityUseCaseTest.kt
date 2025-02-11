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

import app.cash.turbine.test
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.InteractionAvailability
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestConversationDetails
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.logic.util.arrangement.provider.CurrentClientIdProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CurrentClientIdProviderArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangementImpl
import com.wire.kalium.util.KaliumDispatcher
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.once
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ObserveConversationInteractionAvailabilityUseCaseTest {

    @Test
    fun givenUserIsAGroupMember_whenInvokingInteractionForConversation_thenInteractionShouldBeEnabled() = runTest {
        val conversationId = TestConversation.ID

        val (arrangement, observeConversationInteractionAvailability) = arrange {
            withIsClientMlsCapable(false.right())
            dispatcher = testKaliumDispatcher
            withSelfUserBeingMemberOfConversation(isMember = true)
        }

        observeConversationInteractionAvailability(conversationId).test {
            val interactionResult = awaitItem()
            assertEquals(IsInteractionAvailableResult.Success(InteractionAvailability.ENABLED), interactionResult)

            coVerify {
                arrangement.conversationRepository.observeConversationDetailsById(eq(conversationId))
            }.wasInvoked(exactly = once)

            awaitComplete()
        }

    }

    @Test
    fun givenUserIsNoLongerAGroupMember_whenInvokingInteractionForConversation_thenInteractionShouldBeEnabled() = runTest {
        val conversationId = TestConversation.ID

        val (arrangement, observeConversationInteractionAvailability) = arrange {
            dispatcher = testKaliumDispatcher
            withSelfUserBeingMemberOfConversation(isMember = false)
            withIsClientMlsCapable(false.right())
        }

        observeConversationInteractionAvailability(conversationId).test {
            val interactionResult = awaitItem()
            assertEquals(IsInteractionAvailableResult.Success(InteractionAvailability.NOT_MEMBER), interactionResult)

            coVerify {
                arrangement.conversationRepository.observeConversationDetailsById(eq(conversationId))
            }.wasInvoked(exactly = once)

            awaitComplete()
        }

    }

    @Test
    fun givenGroupDetailsReturnsError_whenInvokingInteractionForConversation_thenInteractionShouldReturnFailure() = runTest {
        val conversationId = TestConversation.ID

        val (arrangement, observeConversationInteractionAvailability) = arrange {
            withIsClientMlsCapable(false.right())
            dispatcher = testKaliumDispatcher
            withGroupConversationError()
        }

        observeConversationInteractionAvailability(conversationId).test {
            val interactionResult = awaitItem()
            assertIs<IsInteractionAvailableResult.Failure>(interactionResult)

            coVerify {
                arrangement.conversationRepository.observeConversationDetailsById(eq(conversationId))
            }.wasInvoked(exactly = once)

            awaitComplete()
        }

    }

    @Test
    fun givenOtherUserIsBlocked_whenInvokingInteractionForConversation_thenInteractionShouldBeDisabled() = runTest {
        val conversationId = TestConversation.ID

        val (arrangement, observeConversationInteractionAvailability) = arrange {
            withIsClientMlsCapable(false.right())
            dispatcher = testKaliumDispatcher
            withBlockedUserConversation()
        }

        observeConversationInteractionAvailability(conversationId).test {
            val interactionResult = awaitItem()
            assertEquals(IsInteractionAvailableResult.Success(InteractionAvailability.BLOCKED_USER), interactionResult)

            coVerify {
                arrangement.conversationRepository.observeConversationDetailsById(eq(conversationId))
            }.wasInvoked(exactly = once)

            awaitComplete()
        }
    }

    @Test
    fun givenOtherUserIsDeleted_whenInvokingInteractionForConversation_thenInteractionShouldBeDisabled() = runTest {
        val conversationId = TestConversation.ID

        val (arrangement, observeConversationInteractionAvailability) = arrange {
            withIsClientMlsCapable(false.right())
            dispatcher = testKaliumDispatcher
            withDeletedUserConversation()
        }

        observeConversationInteractionAvailability(conversationId).test {
            val interactionResult = awaitItem()
            assertEquals(IsInteractionAvailableResult.Success(InteractionAvailability.DELETED_USER), interactionResult)

            coVerify {
                arrangement.conversationRepository.observeConversationDetailsById(eq(conversationId))
            }.wasInvoked(exactly = once)

            awaitComplete()
        }
    }

    @Ignore // is this really a case that a client does not support Proteus
    @Test
    fun givenProteusConversationAndUserSupportsOnlyMLS_whenObserving_thenShouldReturnUnsupportedProtocol() = runTest {
        testProtocolSupport(
            conversationProtocolInfo = Conversation.ProtocolInfo.Proteus,
            isMlsCapable = true.right(),
            expectedResult = InteractionAvailability.UNSUPPORTED_PROTOCOL
        )
    }

    @Test
    fun givenMLSConversationAndUserSupportsOnlyMLS_whenObserving_thenShouldReturnUnsupportedProtocol() = runTest {
        testProtocolSupport(
            conversationProtocolInfo = TestConversation.MLS_PROTOCOL_INFO,
            isMlsCapable = false.right(),
            expectedResult = InteractionAvailability.UNSUPPORTED_PROTOCOL
        )
    }

    @Test
    fun givenMixedConversationAndUserSupportsOnlyMLS_whenObserving_thenShouldReturnUnsupportedProtocol() = runTest {
        testProtocolSupport(
            conversationProtocolInfo = TestConversation.MIXED_PROTOCOL_INFO,
            isMlsCapable = false.right(),
            expectedResult = InteractionAvailability.ENABLED
        )
    }

    @Test
    fun givenMixedConversationAndUserSupportsProteus_whenObserving_thenShouldReturnEnabled() = runTest {
        testProtocolSupport(
            conversationProtocolInfo = TestConversation.MIXED_PROTOCOL_INFO,
            isMlsCapable = false.right(),
            expectedResult = InteractionAvailability.ENABLED
        )
    }

    @Test
    fun givenMLSConversationAndUserSupportsMLS_whenObserving_thenShouldReturnEnabled() = runTest {
        testProtocolSupport(
            conversationProtocolInfo = TestConversation.MLS_PROTOCOL_INFO,
            expectedResult = InteractionAvailability.ENABLED,
            isMlsCapable = true.right()
        )
    }

    @Test
    fun givenProteusConversationAndUserSupportsProteus_whenObserving_thenShouldReturnEnabled() = runTest {
        testProtocolSupport(
            conversationProtocolInfo = TestConversation.PROTEUS_PROTOCOL_INFO,
            expectedResult = InteractionAvailability.ENABLED,
            isMlsCapable = false.right()
        )
    }

    private suspend fun CoroutineScope.testProtocolSupport(
        conversationProtocolInfo: Conversation.ProtocolInfo,
        isMlsCapable: Either<StorageFailure, Boolean>,
        expectedResult: InteractionAvailability
    ) {
        val convId = TestConversationDetails.CONVERSATION_GROUP.conversation.id
        val (_, observeConversationInteractionAvailabilityUseCase) = arrange {
            withIsClientMlsCapable(isMlsCapable)
            dispatcher = testKaliumDispatcher
            val proteusGroupDetails = TestConversationDetails.CONVERSATION_GROUP.copy(
                conversation = TestConversationDetails.CONVERSATION_GROUP.conversation.copy(
                    protocol = conversationProtocolInfo
                )
            )
            withObserveConversationDetailsByIdReturning(Either.Right(proteusGroupDetails))
        }

        observeConversationInteractionAvailabilityUseCase(convId).test {
            val result = awaitItem()
            assertIs<IsInteractionAvailableResult.Success>(result)
            assertEquals(expectedResult, result.interactionAvailability)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenConversationLegalHoldIsEnabled_whenInvokingInteractionForConversation_thenInteractionShouldBeEnabled() = runTest {
        val conversationId = TestConversation.ID
        val (_, observeConversationInteractionAvailability) = arrange {
            dispatcher = testKaliumDispatcher
            withLegalHoldOneOnOneConversation(Conversation.LegalHoldStatus.ENABLED)
            withIsClientMlsCapable(false.right())
        }
        observeConversationInteractionAvailability(conversationId).test {
            val interactionResult = awaitItem()
            assertEquals(IsInteractionAvailableResult.Success(InteractionAvailability.ENABLED), interactionResult)
            awaitComplete()
        }
    }

    @Test
    fun givenConversationLegalHoldIsDegraded_whenInvokingInteractionForConversation_thenInteractionShouldBeLegalHold() = runTest {
        val conversationId = TestConversation.ID
        val (_, observeConversationInteractionAvailability) = arrange {
            withIsClientMlsCapable(false.right())
            dispatcher = testKaliumDispatcher
            withLegalHoldOneOnOneConversation(Conversation.LegalHoldStatus.DEGRADED)
        }
        observeConversationInteractionAvailability(conversationId).test {
            val interactionResult = awaitItem()
            assertEquals(IsInteractionAvailableResult.Success(InteractionAvailability.LEGAL_HOLD), interactionResult)
            awaitComplete()
        }
    }

    private class Arrangement(
        private val configure: suspend Arrangement.() -> Unit
    ) : UserRepositoryArrangement by UserRepositoryArrangementImpl(),
        ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl(),
        CurrentClientIdProviderArrangement by CurrentClientIdProviderArrangementImpl() {

        var dispatcher: KaliumDispatcher = TestKaliumDispatcher

        val selfUser = UserId("self_value", "self_domain")
        suspend fun withSelfUserBeingMemberOfConversation(isMember: Boolean) = apply {
            withObserveConversationDetailsByIdReturning(
                Either.Right(TestConversationDetails.CONVERSATION_GROUP.copy(isSelfUserMember = isMember))
            )
        }

        suspend fun withGroupConversationError() {
            withObserveConversationDetailsByIdReturning(Either.Left(StorageFailure.DataNotFound))
        }

        suspend fun withBlockedUserConversation() = apply {
            withObserveConversationDetailsByIdReturning(
                Either.Right(
                    TestConversationDetails.CONVERSATION_ONE_ONE.copy(
                        otherUser = TestUser.OTHER.copy(
                            connectionStatus = ConnectionState.BLOCKED
                        )
                    )
                )
            )
        }

        suspend fun withDeletedUserConversation() = apply {
            withObserveConversationDetailsByIdReturning(
                Either.Right(
                    TestConversationDetails.CONVERSATION_ONE_ONE.copy(
                        otherUser = TestUser.OTHER.copy(
                            deleted = true
                        )
                    )
                )
            )
        }

        suspend fun withLegalHoldOneOnOneConversation(legalHoldStatus: Conversation.LegalHoldStatus) = apply {
            withObserveConversationDetailsByIdReturning(
                Either.Right(
                    TestConversationDetails.CONVERSATION_ONE_ONE.copy(
                        conversation = TestConversation.ONE_ON_ONE().copy(legalHoldStatus = legalHoldStatus)
                    )
                )
            )
        }

        suspend fun arrange(): Pair<Arrangement, ObserveConversationInteractionAvailabilityUseCase> = run {
            withCurrentClientIdSuccess(ClientId("client_id"))
            configure()
            this@Arrangement to ObserveConversationInteractionAvailabilityUseCase(
                conversationRepository = conversationRepository,
                userRepository = userRepository,
                dispatcher = dispatcher,
                selfUserId = selfUser,
                selfClientIdProvider = currentClientIdProvider
                )
        }
    }

    private companion object {
        suspend fun arrange(configure: suspend Arrangement.() -> Unit) = Arrangement(configure).arrange()
    }
}
