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
import com.wire.kalium.cryptography.WelcomeBundle
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.FetchConversationIfUnknownUseCase
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.e2ei.CertificateRevocationListRepository
import com.wire.kalium.logic.data.e2ei.RevocationListChecker
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.feature.keypackage.RefillKeyPackagesResult
import com.wire.kalium.logic.feature.keypackage.RefillKeyPackagesUseCase
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
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
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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
            arrangement.refillKeyPackagesUseCase.invoke(any())
        }
    }

    @Test
    fun givenProcessingOfWelcomeSucceeds_thenShouldFetchConversationIfUnknown() = runTest {
        val (arrangement, mlsWelcomeEventHandler) = arrange {
            withRefillKeyPackagesReturning(RefillKeyPackagesResult.Success)
            withMLSClientProcessingOfWelcomeMessageReturnsSuccessfully()
            withFetchConversationIfUnknownSucceeding()
            withUpdateGroupStateReturning(Either.Right(Unit))
            withObserveConversationDetailsByIdReturning(Either.Right(CONVERSATION_GROUP))
        }

        mlsWelcomeEventHandler.handle(arrangement.transactionContext, WELCOME_EVENT).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.fetchConversationIfUnknown(any(), eq(CONVERSATION_ID), any())
        }
    }

    @Test
    fun givenProcessingOfWelcomeSucceeds_thenShouldMarkConversationAsEstablished() = runTest {
        val (arrangement, mlsWelcomeEventHandler) = arrange {
            withRefillKeyPackagesReturning(RefillKeyPackagesResult.Success)
            withMLSClientProcessingOfWelcomeMessageReturnsSuccessfully()
            withFetchConversationIfUnknownSucceeding()
            withUpdateGroupStateReturning(Either.Right(Unit))
            withObserveConversationDetailsByIdReturning(Either.Right(CONVERSATION_GROUP))
        }

        mlsWelcomeEventHandler.handle(arrangement.transactionContext, WELCOME_EVENT).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.updateConversationGroupState(
                eq(GroupID(MLS_GROUP_ID)),
                eq(Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED)
            )
        }
    }

    @Test
    fun givenProcessingOfWelcomeForOneOnOneSucceeds_thenShouldResolveConversation() = runTest {
        val (arrangement, mlsWelcomeEventHandler) = arrange {
            withRefillKeyPackagesReturning(RefillKeyPackagesResult.Success)
            withMLSClientProcessingOfWelcomeMessageReturnsSuccessfully()
            withFetchConversationIfUnknownSucceeding()
            withUpdateGroupStateReturning(Either.Right(Unit))
            withObserveConversationDetailsByIdReturning(Either.Right(CONVERSATION_ONE_ONE))
            withResolveOneOnOneConversationWithUserReturning(Either.Right(CONVERSATION_ID))
        }

        mlsWelcomeEventHandler.handle(arrangement.transactionContext, WELCOME_EVENT).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.oneOnOneResolver.resolveOneOnOneConversationWithUser(any(), eq(CONVERSATION_ONE_ONE.otherUser), any())
        }
    }

    @Test
    fun givenProcessingOfWelcomeForGroupSucceeds_thenShouldNotResolveConversation() = runTest {
        val (arrangement, mlsWelcomeEventHandler) = arrange {
            withRefillKeyPackagesReturning(RefillKeyPackagesResult.Success)
            withMLSClientProcessingOfWelcomeMessageReturnsSuccessfully()
            withFetchConversationIfUnknownSucceeding()
            withUpdateGroupStateReturning(Either.Right(Unit))
            withObserveConversationDetailsByIdReturning(Either.Right(CONVERSATION_GROUP))
        }

        mlsWelcomeEventHandler.handle(arrangement.transactionContext, WELCOME_EVENT).shouldSucceed()

        verifySuspend(VerifyMode.not) {
            arrangement.oneOnOneResolver.resolveOneOnOneConversationWithUser(any(), any(), any())
        }
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
    fun givenResolveOneOnOneConversationFails_thenShouldPropagateError() = runTest {

        val failure = Either.Left(NetworkFailure.NoNetworkConnection(null))
        val (arrangement, mlsWelcomeEventHandler) = arrange {
            withMLSClientProcessingOfWelcomeMessageReturnsSuccessfully()
            withFetchConversationIfUnknownSucceeding()
            withUpdateGroupStateReturning(Either.Right(Unit))
            withObserveConversationDetailsByIdReturning(Either.Right(CONVERSATION_ONE_ONE))
            withResolveOneOnOneConversationWithUserReturning(failure)
        }

        mlsWelcomeEventHandler.handle(arrangement.transactionContext, WELCOME_EVENT).shouldFail {
            assertEquals(failure.value, it)
        }
    }

    @Test
    fun givenResolveOneOnOneConversationFails_thenShouldNotAttemptToRefillKeyPackages() = runTest {
        val failure = Either.Left(NetworkFailure.NoNetworkConnection(null))
        val (arrangement, mlsWelcomeEventHandler) = arrange {
            withMLSClientProcessingOfWelcomeMessageReturnsSuccessfully()
            withFetchConversationIfUnknownSucceeding()
            withUpdateGroupStateReturning(Either.Right(Unit))
            withObserveConversationDetailsByIdReturning(Either.Right(CONVERSATION_ONE_ONE))
            withResolveOneOnOneConversationWithUserReturning(failure)
        }

        mlsWelcomeEventHandler.handle(arrangement.transactionContext, WELCOME_EVENT)

        verifySuspend(VerifyMode.not) {
            arrangement.refillKeyPackagesUseCase.invoke(any())
        }
    }

    @Test
    fun givenAllSucceeds_whenHandlingEvent_thenShouldAttemptToRefillKeyPackages() = runTest {
        val (arrangement, mlsWelcomeEventHandler) = arrange {
            withRefillKeyPackagesReturning(RefillKeyPackagesResult.Success)
            withMLSClientProcessingOfWelcomeMessageReturnsSuccessfully()
            withFetchConversationIfUnknownSucceeding()
            withUpdateGroupStateReturning(Either.Right(Unit))
            withObserveConversationDetailsByIdReturning(Either.Right(CONVERSATION_GROUP))
        }

        mlsWelcomeEventHandler.handle(arrangement.transactionContext, WELCOME_EVENT)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.refillKeyPackagesUseCase.invoke(any())
        }
    }

    @Test
    fun givenWelcomeBundleWithNewDistributionsCRL_whenHandlingEvent_then_CheckRevocationList() = runTest {
        val failure = Either.Left(StorageFailure.DataNotFound)
        val (arrangement, mlsWelcomeEventHandler) = arrange {
            withMLSClientProcessingOfWelcomeMessageReturnsSuccessfully(
                WELCOME_BUNDLE.copy(crlNewDistributionPoints = listOf("url"))
            )
            withFetchConversationIfUnknownSucceeding()
            withCheckRevocationListResult()
            withUpdateGroupStateReturning(failure)
        }

        mlsWelcomeEventHandler.handle(arrangement.transactionContext, WELCOME_EVENT)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.fetchConversationIfUnknown(any(), eq(CONVERSATION_ID), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.checkRevocationList.check(any(), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.certificateRevocationListRepository.addOrUpdateCRL(any(), any())
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
            withRefillKeyPackagesReturning(RefillKeyPackagesResult.Success)
        }

        mlsWelcomeEventHandler.handle(arrangement.transactionContext, WELCOME_EVENT).shouldSucceed()

        verifySuspend(VerifyMode.not) {
            arrangement.joinExistingMLSConversation.invoke(any(), any(), any(), eq(true))
        }

        verifySuspend(VerifyMode.not) {
            arrangement.conversationRepository.updateConversationGroupState(any(), any())
        }
    }

    @Test
    fun givenOrphanWelcomeAndLocalGroupNotEstablished_whenHandlingWelcome_thenShouldRejoinByExternalCommit() = runTest {
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
            withJoinExistingMLSConversationReturning(Either.Right(Unit))
            withRefillKeyPackagesReturning(RefillKeyPackagesResult.Success)
        }

        mlsWelcomeEventHandler.handle(arrangement.transactionContext, WELCOME_EVENT).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.joinExistingMLSConversation.invoke(any(), eq(CONVERSATION_ID), any(), eq(true))
        }
    }
    private class Arrangement(private val block: suspend Arrangement.() -> Unit) :
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMokkeryImpl() {
        val conversationRepository = mock<ConversationRepository>()
        val fetchConversationIfUnknown = mock<FetchConversationIfUnknownUseCase>()
        val oneOnOneResolver = mock<OneOnOneResolver>()
        val refillKeyPackagesUseCase = mock<RefillKeyPackagesUseCase>()
        val checkRevocationList = mock<RevocationListChecker>()
        val certificateRevocationListRepository = mock<CertificateRevocationListRepository>()
        val joinExistingMLSConversation = mock<JoinExistingMLSConversationUseCase>()

        suspend fun withMLSClientProcessingOfWelcomeMessageFailsWith(exception: Exception) = apply {
            everySuspend {
                mlsContext.processWelcomeMessage(any())
            } throws exception
        }

        suspend fun withMLSClientProcessingOfWelcomeMessageReturnsSuccessfully(welcomeBundle: WelcomeBundle = WELCOME_BUNDLE) = apply {
            everySuspend {
                mlsContext.processWelcomeMessage(any())
            } returns welcomeBundle
        }

        suspend fun withMLSConversationExists(exists: Boolean) = apply {
            everySuspend {
                mlsContext.conversationExists(any())
            } returns exists
        }

        suspend fun withJoinExistingMLSConversationReturning(result: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                joinExistingMLSConversation.invoke(any(), any(), any(), eq(true))
            } returns result
        }
        suspend fun withCheckRevocationListResult() {
            everySuspend {
                checkRevocationList.check(any(), any())
            } returns Either.Right(1uL)
            everySuspend {
                certificateRevocationListRepository.addOrUpdateCRL(any(), any())
            } returns Unit
        }

        suspend fun withRefillKeyPackagesReturning(result: RefillKeyPackagesResult) = apply {
            everySuspend {
                refillKeyPackagesUseCase.invoke(any())
            } returns result
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

        suspend fun withResolveOneOnOneConversationWithUserReturning(result: Either<CoreFailure, com.wire.kalium.logic.data.id.ConversationId>) = apply {
            everySuspend { oneOnOneResolver.resolveOneOnOneConversationWithUser(any(), any(), any()) } returns result
        }

        suspend fun withConversationProtocolInfo(result: Either<StorageFailure, Conversation.ProtocolInfo>) = apply {
            everySuspend { conversationRepository.getConversationProtocolInfo(any()) } returns result
        }

        suspend fun arrange() = run {
            block()
            this@Arrangement to MLSWelcomeEventHandlerImpl(
                conversationRepository = conversationRepository,
                oneOnOneResolver = oneOnOneResolver,
                refillKeyPackages = refillKeyPackagesUseCase,
                revocationListChecker = checkRevocationList,
                certificateRevocationListRepository = certificateRevocationListRepository,
                joinExistingMLSConversation = joinExistingMLSConversation,
                fetchConversationIfUnknown = fetchConversationIfUnknown
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
            timestampIso = "2022-03-30T15:36:00.000Z"
        )
        val WELCOME_BUNDLE = WelcomeBundle(MLS_GROUP_ID, null)
    }
}
