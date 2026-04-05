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
import com.wire.kalium.logic.data.MockConversation
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.FetchConversationIfUnknownUseCase
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.e2ei.CertificateRevocationListRepository
import com.wire.kalium.logic.data.e2ei.RevocationListChecker
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.logic.feature.keypackage.RefillKeyPackagesResult
import com.wire.kalium.logic.feature.keypackage.RefillKeyPackagesUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestConversationDetails
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.matcher.any as mokkeryAny
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import io.ktor.util.encodeBase64
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
            arrangement.conversationRepository.updateConversationGroupState(mokkeryAny(), mokkeryAny())
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
            arrangement.conversationRepository.updateConversationGroupState(mokkeryAny(), mokkeryAny())
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
            arrangement.fetchConversationIfUnknown(mokkeryAny(), CONVERSATION_ID, mokkeryAny())
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
                GroupID(MLS_GROUP_ID),
                Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED
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
            arrangement.oneOnOneResolver.resolveOneOnOneConversationWithUser(mokkeryAny(), CONVERSATION_ONE_ONE.otherUser, mokkeryAny())
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
            arrangement.oneOnOneResolver.resolveOneOnOneConversationWithUser(mokkeryAny(), mokkeryAny(), mokkeryAny())
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
            arrangement.refillKeyPackagesUseCase.invoke(mokkeryAny())
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
            arrangement.refillKeyPackagesUseCase.invoke(mokkeryAny())
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
            arrangement.fetchConversationIfUnknown(mokkeryAny(), CONVERSATION_ID, mokkeryAny())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.checkRevocationList.check(mokkeryAny(), mokkeryAny())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.certificateRevocationListRepository.addOrUpdateCRL(mokkeryAny(), mokkeryAny())
        }
    }

    @Test
    fun givenLocalConversationExists_whenHandlingWelcome_thenShouldWipeAndRetryProcessing() = runTest {
        val (arrangement, mlsWelcomeEventHandler) = arrange {
            withMLSClientProcessingOfWelcomeMessageFailingOnceWithConversationAlreadyExistsThenSuccess()
            withFetchConversationIfUnknownSucceeding()
            withConversationProtocolInfo(Either.Right(MockConversation.MLS_PROTOCOL_INFO))
            withWipeConversationReturningSuccessfully()
            withUpdateGroupStateReturning(Either.Right(Unit))
            withObserveConversationDetailsByIdReturning(Either.Right(CONVERSATION_GROUP))
            withRefillKeyPackagesReturning(RefillKeyPackagesResult.Success)
        }

        mlsWelcomeEventHandler.handle(arrangement.transactionContext, WELCOME_EVENT).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsContext.wipeConversation(mokkeryAny())
        }

        verifySuspend(VerifyMode.exactly(2)) {
            arrangement.mlsContext.processWelcomeMessage(mokkeryAny())
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
            arrangement.joinExistingMLSConversation.invoke(mokkeryAny(), mokkeryAny(), mokkeryAny())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.conversationRepository.updateConversationGroupState(mokkeryAny(), mokkeryAny())
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
            arrangement.joinExistingMLSConversation.invoke(mokkeryAny(), CONVERSATION_ID, mokkeryAny())
        }
    }

    private class Arrangement(private val block: suspend Arrangement.() -> Unit) :
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val conversationRepository = mock<ConversationRepository>()
        val fetchConversationIfUnknown = mock<FetchConversationIfUnknownUseCase>()
        val oneOnOneResolver = mock<OneOnOneResolver>()
        val refillKeyPackagesUseCase = mock<RefillKeyPackagesUseCase>()
        val checkRevocationList = mock<RevocationListChecker>()
        val certificateRevocationListRepository = mock<CertificateRevocationListRepository>(mode = MockMode.autoUnit)
        val joinExistingMLSConversation = mock<JoinExistingMLSConversationUseCase>()

        suspend fun withMLSClientProcessingOfWelcomeMessageFailsWith(exception: Exception) = apply {
            everySuspend { mlsContext.processWelcomeMessage(mokkeryAny()) } throws exception
        }

        suspend fun withMLSClientProcessingOfWelcomeMessageFailingOnceWithConversationAlreadyExistsThenSuccess(
            callCountToFail: Int = 1,
            welcomeBundle: WelcomeBundle = WELCOME_BUNDLE
        ) = apply {
            var callCount = 0
            everySuspend { mlsContext.processWelcomeMessage(mokkeryAny()) } calls {
                callCount += 1
                if (callCount == callCountToFail) {
                    throw CommonizedMLSException(
                        MLSFailure.ConversationAlreadyExists,
                        IllegalStateException("conversation already exists")
                    )
                }
                welcomeBundle
            }
        }

        suspend fun withMLSClientProcessingOfWelcomeMessageReturnsSuccessfully(welcomeBundle: WelcomeBundle = WELCOME_BUNDLE) = apply {
            everySuspend { mlsContext.processWelcomeMessage(mokkeryAny()) } returns welcomeBundle
        }

        suspend fun withWipeConversationReturningSuccessfully() = apply {
            everySuspend { mlsContext.wipeConversation(mokkeryAny()) } returns Unit
        }

        suspend fun withMLSConversationExists(exists: Boolean) = apply {
            everySuspend { mlsContext.conversationExists(mokkeryAny()) } returns exists
        }

        suspend fun withFetchConversationIfUnknownFailingWith(coreFailure: CoreFailure) = apply {
            everySuspend {
                fetchConversationIfUnknown.invoke(mokkeryAny(), mokkeryAny(), mokkeryAny())
            } returns Either.Left(coreFailure)
        }

        suspend fun withFetchConversationIfUnknownSucceeding() = apply {
            everySuspend {
                fetchConversationIfUnknown.invoke(mokkeryAny(), mokkeryAny(), mokkeryAny())
            } returns Either.Right(Unit)
        }

        suspend fun withUpdateGroupStateReturning(result: Either<StorageFailure, Unit>) = apply {
            everySuspend {
                conversationRepository.updateConversationGroupState(mokkeryAny(), mokkeryAny())
            } returns result
        }

        suspend fun withObserveConversationDetailsByIdReturning(vararg results: Either<StorageFailure, ConversationDetails>) = apply {
            everySuspend {
                conversationRepository.observeConversationDetailsById(mokkeryAny())
            } returns flowOf(*results)
        }

        suspend fun withConversationProtocolInfo(result: Either<StorageFailure, Conversation.ProtocolInfo>) = apply {
            everySuspend {
                conversationRepository.getConversationProtocolInfo(mokkeryAny())
            } returns result
        }

        suspend fun withResolveOneOnOneConversationWithUserReturning(result: Either<CoreFailure, com.wire.kalium.logic.data.id.ConversationId>) = apply {
            everySuspend {
                oneOnOneResolver.resolveOneOnOneConversationWithUser(mokkeryAny(), mokkeryAny(), mokkeryAny())
            } returns result
        }

        suspend fun withJoinExistingMLSConversationReturning(result: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                joinExistingMLSConversation.invoke(mokkeryAny(), mokkeryAny(), mokkeryAny())
            } returns result
        }

        suspend fun withCheckRevocationListResult() = apply {
            everySuspend {
                checkRevocationList.check(mokkeryAny(), mokkeryAny())
            } returns Either.Right(1uL)
        }

        suspend fun withRefillKeyPackagesReturning(result: RefillKeyPackagesResult) = apply {
            everySuspend {
                refillKeyPackagesUseCase.invoke(mokkeryAny())
            } returns result
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
            WELCOME.encodeBase64(),
            timestampIso = "2022-03-30T15:36:00.000Z"
        )
        val WELCOME_BUNDLE = WelcomeBundle(MLS_GROUP_ID, null)
    }
}
