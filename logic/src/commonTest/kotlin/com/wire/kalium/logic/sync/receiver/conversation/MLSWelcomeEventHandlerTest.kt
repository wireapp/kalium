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
package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.common.error.CommonizedMLSException
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.MLSGroupId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.FetchConversationIfUnknownUseCase
import com.wire.kalium.logic.data.conversation.mls.PendingActionsRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.feature.keypackage.KeyPackageManager
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestConversationDetails
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMokkeryImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.MockMode
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import dev.mokkery.verify
import kotlin.io.encoding.Base64
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MLSWelcomeEventHandlerTest {

    @Test
    fun givenMLSClientFailsProcessingOfWelcomeMessageFails_thenShouldNotMarkConversationAsEstablished() = runTest {
        val exception = RuntimeException()

        val (arrangement, mlsWelcomeEventHandler) = arrange {
            withFetchConversationIfUnknownSucceeding()
            withMLSClientProcessingOfWelcomeMessageFailsWith(exception)
        }

        mlsWelcomeEventHandler.handle(arrangement.transactionContext, WELCOME_EVENT).shouldFail()

        verifySuspend(VerifyMode.not) {
            arrangement.conversationRepository.updateConversationGroupState(any(), any())
        }
    }

    @Test
    fun givenConversationFetchFails_thenShouldNotMarkConversationAsEstablished() = runTest {
        val failure = CoreFailure.Unknown(null)
        val (arrangement, mlsWelcomeEventHandler) = arrange {
            withMLSClientProcessingOfWelcomeMessageReturnsSuccessfully()
            withFetchConversationIfUnknownFailingWith(failure)
        }

        mlsWelcomeEventHandler.handle(arrangement.transactionContext, WELCOME_EVENT).shouldFail()

        verifySuspend(VerifyMode.not) {
            arrangement.conversationRepository.updateConversationGroupState(any(), any())
        }
    }

    @Test
    fun givenConversationWasDeleted_whenHandlingWelcome_thenShouldSkipStaleEvent() = runTest {
        val failure = NetworkFailure.ServerMiscommunication(TestNetworkException.noConversation)
        val (arrangement, mlsWelcomeEventHandler) = arrange {
            withFetchConversationIfUnknownFailingWith(failure)
        }

        mlsWelcomeEventHandler.handle(arrangement.transactionContext, WELCOME_EVENT).shouldSucceed()

        verifySuspend(VerifyMode.not) {
            arrangement.mlsContext.processWelcomeMessage(any())
        }
        verify(VerifyMode.not) {
            arrangement.keyPackageManager.scheduleRefill()
        }
    }

    @Test
    fun givenConversationAccessDenied_whenHandlingWelcome_thenShouldSkipStaleEvent() = runTest {
        val failure = NetworkFailure.ServerMiscommunication(TestNetworkException.accessDenied)
        val (arrangement, mlsWelcomeEventHandler) = arrange {
            withFetchConversationIfUnknownFailingWith(failure)
        }

        mlsWelcomeEventHandler.handle(arrangement.transactionContext, WELCOME_EVENT).shouldSucceed()

        verifySuspend(VerifyMode.not) {
            arrangement.mlsContext.processWelcomeMessage(any())
        }
        verify(VerifyMode.not) {
            arrangement.keyPackageManager.scheduleRefill()
        }
    }

    @Test
    fun givenProcessingOfWelcomeSucceeds_thenShouldFetchConversationIfUnknown() = runTest {
        val (arrangement, mlsWelcomeEventHandler) = arrange {
            withMLSClientProcessingOfWelcomeMessageReturnsSuccessfully()
            withFetchConversationIfUnknownSucceeding()
            withUpdateGroupStateReturning(Either.Right(Unit))
            withObserveConversationDetailsByIdReturning(Either.Right(CONVERSATION_GROUP))
        }

        mlsWelcomeEventHandler.handle(arrangement.transactionContext, WELCOME_EVENT).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.fetchConversationIfUnknown(any(), CONVERSATION_ID, any())
        }
    }

    @Test
    fun givenProcessingOfWelcomeSucceeds_thenShouldMarkConversationAsEstablished() = runTest {
        val (arrangement, mlsWelcomeEventHandler) = arrange {
            withMLSClientProcessingOfWelcomeMessageReturnsSuccessfully()
            withFetchConversationIfUnknownSucceeding()
            withUpdateGroupStateReturning(Either.Right(Unit))
            withObserveConversationDetailsByIdReturning(Either.Right(CONVERSATION_GROUP))
        }

        mlsWelcomeEventHandler.handle(arrangement.transactionContext, WELCOME_EVENT).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.updateConversationGroupState(
                GroupID(MLS_GROUP_ID),
                Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED
            )
        }
    }

    @Test
    fun givenProcessingOfWelcomeForOneOnOneSucceeds_thenShouldQueueResolutionInsteadOfResolvingInline() = runTest {
        val (arrangement, mlsWelcomeEventHandler) = arrange {
            withMLSClientProcessingOfWelcomeMessageReturnsSuccessfully()
            withFetchConversationIfUnknownSucceeding()
            withUpdateGroupStateReturning(Either.Right(Unit))
            withObserveConversationDetailsByIdReturning(Either.Right(CONVERSATION_ONE_ONE))
            withEnqueueOneOnOneResolutionSucceeding()
        }

        mlsWelcomeEventHandler.handle(arrangement.transactionContext, WELCOME_EVENT).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.pendingActionsRepository.enqueuePendingOneOnOneResolution(CONVERSATION_ONE_ONE.otherUser.id)
        }
        assertEquals(1, arrangement.resolutionTriggers)
    }

    @Test
    fun givenProcessingOfWelcomeForGroupSucceeds_thenShouldNotResolveConversation() = runTest {
        val (arrangement, mlsWelcomeEventHandler) = arrange {
            withMLSClientProcessingOfWelcomeMessageReturnsSuccessfully()
            withFetchConversationIfUnknownSucceeding()
            withUpdateGroupStateReturning(Either.Right(Unit))
            withObserveConversationDetailsByIdReturning(Either.Right(CONVERSATION_GROUP))
        }

        mlsWelcomeEventHandler.handle(arrangement.transactionContext, WELCOME_EVENT).shouldSucceed()

        verifySuspend(VerifyMode.not) {
            arrangement.pendingActionsRepository.enqueuePendingOneOnOneResolution(any())
        }
        assertEquals(0, arrangement.resolutionTriggers)
    }

    @Test
    fun givenUpdateGroupStateFails_thenShouldPropagateError() = runTest {

        val failure = Either.Left(StorageFailure.DataNotFound)
        val (arrangement, mlsWelcomeEventHandler) = arrange {
            withMLSClientProcessingOfWelcomeMessageReturnsSuccessfully()
            withFetchConversationIfUnknownSucceeding()
            withUpdateGroupStateReturning(failure)
        }

        mlsWelcomeEventHandler.handle(arrangement.transactionContext, WELCOME_EVENT).shouldFail {
            assertEquals(failure.value, it)
        }
    }

    @Test
    fun givenAllSucceeds_whenHandlingEvent_thenShouldAttemptToRefillKeyPackages() = runTest {
        val (arrangement, mlsWelcomeEventHandler) = arrange {
            withMLSClientProcessingOfWelcomeMessageReturnsSuccessfully()
            withFetchConversationIfUnknownSucceeding()
            withUpdateGroupStateReturning(Either.Right(Unit))
            withObserveConversationDetailsByIdReturning(Either.Right(CONVERSATION_GROUP))
        }

        mlsWelcomeEventHandler.handle(arrangement.transactionContext, WELCOME_EVENT)

        verify(VerifyMode.exactly(1)) {
            arrangement.keyPackageManager.scheduleRefill()
        }
    }

    @Test
    fun givenOrphanWelcomeAndLocalGroupAlreadyEstablished_whenHandlingWelcome_thenShouldSkipExternalCommitRejoin() = runTest {
        val orphanWelcomeException = CommonizedMLSException(
            MLSFailure.OrphanWelcome,
            IllegalStateException("key package already deleted locally")
        )
        val (arrangement, mlsWelcomeEventHandler) = arrange {
            withFetchConversationIfUnknownSucceeding()
            withMLSClientProcessingOfWelcomeMessageFailsWith(orphanWelcomeException)
            withConversationProtocolInfo(
                Either.Right(
                    TestConversation.MLS_PROTOCOL_INFO.copy(
                        groupState = Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED
                    )
                )
            )
            withMLSConversationExists(true)
        }

        mlsWelcomeEventHandler.handle(arrangement.transactionContext, WELCOME_EVENT).shouldSucceed()

        verifySuspend(VerifyMode.not) {
            arrangement.pendingActionsRepository.enqueuePendingMLSGroupJoin(any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.conversationRepository.updateConversationGroupState(any(), any())
        }
    }

    @Test
    fun givenOrphanWelcomeAndLocalGroupNotEstablished_whenHandlingWelcome_thenShouldQueueExternalCommitRejoin() = runTest {
        val orphanWelcomeException = CommonizedMLSException(
            MLSFailure.OrphanWelcome,
            IllegalStateException("key package already deleted locally")
        )
        val (arrangement, mlsWelcomeEventHandler) = arrange {
            withFetchConversationIfUnknownSucceeding()
            withMLSClientProcessingOfWelcomeMessageFailsWith(orphanWelcomeException)
            withConversationProtocolInfo(
                Either.Right(
                    TestConversation.MLS_PROTOCOL_INFO.copy(
                        groupState = Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_JOIN
                    )
                )
            )
            withEnqueuePendingMLSGroupJoinSucceeding()
        }

        mlsWelcomeEventHandler.handle(arrangement.transactionContext, WELCOME_EVENT).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.pendingActionsRepository.enqueuePendingMLSGroupJoin(CONVERSATION_ID)
        }
        assertEquals(1, arrangement.joinTriggers)
    }
    private class Arrangement(private val block: suspend Arrangement.() -> Unit) :
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMokkeryImpl() {
        val conversationRepository = mock<ConversationRepository>()
        val fetchConversationIfUnknown = mock<FetchConversationIfUnknownUseCase>()
        val pendingActionsRepository = mock<PendingActionsRepository>()
        var resolutionTriggers = 0
        val keyPackageManager = mock<KeyPackageManager>(mode = MockMode.autoUnit)
        var joinTriggers = 0

        suspend fun withMLSClientProcessingOfWelcomeMessageFailsWith(exception: Exception) = apply {
            everySuspend {
                mlsContext.processWelcomeMessage(any())
            } throws exception
        }

        suspend fun withMLSClientProcessingOfWelcomeMessageReturnsSuccessfully(groupId: MLSGroupId = MLS_GROUP_ID) = apply {
            everySuspend {
                mlsContext.processWelcomeMessage(any())
            } returns groupId
        }

        suspend fun withMLSConversationExists(exists: Boolean) = apply {
            everySuspend {
                mlsContext.conversationExists(any())
            } returns exists
        }

        suspend fun withFetchConversationIfUnknownSucceeding() = apply {
            everySuspend { fetchConversationIfUnknown(any(), any(), any()) } returns Either.Right(Unit)
        }

        suspend fun withFetchConversationIfUnknownFailingWith(coreFailure: CoreFailure) = apply {
            everySuspend { fetchConversationIfUnknown(any(), any(), any()) } returns Either.Left(coreFailure)
        }

        suspend fun withUpdateGroupStateReturning(result: Either<StorageFailure, Unit>) = apply {
            everySuspend { conversationRepository.updateConversationGroupState(any(), any()) } returns result
        }

        suspend fun withObserveConversationDetailsByIdReturning(result: Either<StorageFailure, ConversationDetails>) = apply {
            everySuspend { conversationRepository.observeConversationDetailsById(any()) } returns flowOf(result)
        }

        suspend fun withEnqueueOneOnOneResolutionSucceeding() = apply {
            everySuspend { pendingActionsRepository.enqueuePendingOneOnOneResolution(any()) } returns Unit
        }

        suspend fun withEnqueuePendingMLSGroupJoinSucceeding() = apply {
            everySuspend { pendingActionsRepository.enqueuePendingMLSGroupJoin(any()) } returns Unit
        }

        suspend fun withConversationProtocolInfo(result: Either<StorageFailure, Conversation.ProtocolInfo>) = apply {
            everySuspend { conversationRepository.getConversationProtocolInfo(any()) } returns result
        }

        suspend fun arrange() = run {
            block()
            this@Arrangement to MLSWelcomeEventHandlerImpl(
                conversationRepository = conversationRepository,
                pendingActionsRepository = pendingActionsRepository,
                resolvePendingOneOnOnes = { resolutionTriggers++ },
                joinPendingMLSGroups = { joinTriggers++ },
                fetchConversationIfUnknown = fetchConversationIfUnknown,
                keyPackageManager = keyPackageManager
            )
        }
    }

    private companion object {
        suspend fun arrange(configuration: suspend Arrangement.() -> Unit) = Arrangement(configuration).arrange()

        const val MLS_GROUP_ID: MLSGroupId = "test-mlsGroupId"
        val CONVERSATION_ONE_ONE = TestConversationDetails.CONVERSATION_ONE_ONE
        val CONVERSATION_GROUP = TestConversationDetails.CONVERSATION_GROUP
        val CONVERSATION_ID = TestConversation.ID
        val WELCOME = "welcome".encodeToByteArray()
        val WELCOME_EVENT = Event.Conversation.MLSWelcome(
            "eventId",
            CONVERSATION_ID,
            TestUser.USER_ID,
            Base64.encode(WELCOME),
        )
    }
}
