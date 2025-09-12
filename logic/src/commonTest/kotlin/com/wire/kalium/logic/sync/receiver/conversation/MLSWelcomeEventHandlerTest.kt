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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.MLSGroupId
import com.wire.kalium.cryptography.WelcomeBundle
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.e2ei.CertificateRevocationListRepository
import com.wire.kalium.logic.data.e2ei.RevocationListChecker
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.feature.keypackage.RefillKeyPackagesResult
import com.wire.kalium.logic.feature.keypackage.RefillKeyPackagesUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestConversationDetails
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.arrangement.mls.OneOnOneResolverArrangement
import com.wire.kalium.logic.util.arrangement.mls.OneOnOneResolverArrangementImpl
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.usecase.FetchConversationIfUnknownUseCaseArrangement
import com.wire.kalium.logic.util.arrangement.usecase.FetchConversationIfUnknownUseCaseArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import io.ktor.util.encodeBase64
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
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

        coVerify {
            arrangement.conversationRepository.updateConversationGroupState(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenConversationFetchFails_thenShouldNotMarkConversationAsEstablished() = runTest {
        val failure = CoreFailure.Unknown(null)
        val (arrangement, mlsWelcomeEventHandler) = arrange {
            withMLSClientProcessingOfWelcomeMessageReturnsSuccessfully()
            withFetchConversationIfUnknownFailingWith(failure)
        }

        mlsWelcomeEventHandler.handle(arrangement.transactionContext, WELCOME_EVENT).shouldFail()

        coVerify {
            arrangement.conversationRepository.updateConversationGroupState(any(), any())
        }.wasNotInvoked()
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

        coVerify {
            arrangement.fetchConversationIfUnknown(any(), eq(CONVERSATION_ID), any())
        }.wasInvoked(exactly = once)
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

        coVerify {
            arrangement.conversationRepository.updateConversationGroupState(
                eq(GroupID(MLS_GROUP_ID)),
                eq(Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED)
            )
        }.wasInvoked(exactly = once)
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

        coVerify {
            arrangement.oneOnOneResolver.resolveOneOnOneConversationWithUser(any(), eq(CONVERSATION_ONE_ONE.otherUser), any())
        }.wasInvoked(exactly = once)
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

        coVerify {
            arrangement.oneOnOneResolver.resolveOneOnOneConversationWithUser(any(), any(), any())
        }.wasNotInvoked()
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

        coVerify {
            arrangement.refillKeyPackagesUseCase.invoke(any())
        }.wasNotInvoked()
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

        coVerify {
            arrangement.refillKeyPackagesUseCase.invoke(any())
        }.wasInvoked(exactly = once)
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

        coVerify {
            arrangement.fetchConversationIfUnknown(any(), eq(CONVERSATION_ID), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.checkRevocationList.check(any(), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.certificateRevocationListRepository.addOrUpdateCRL(any(), any())
        }.wasInvoked(exactly = once)
    }

    private class Arrangement(private val block: suspend Arrangement.() -> Unit) :
        ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl(),
        FetchConversationIfUnknownUseCaseArrangement by FetchConversationIfUnknownUseCaseArrangementImpl(),
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl(),
        OneOnOneResolverArrangement by OneOnOneResolverArrangementImpl() {
        val refillKeyPackagesUseCase = mock(RefillKeyPackagesUseCase::class)
        val checkRevocationList = mock(RevocationListChecker::class)
        val certificateRevocationListRepository = mock(CertificateRevocationListRepository::class)
        val joinExistingMLSConversation = mock(JoinExistingMLSConversationUseCase::class)

        suspend fun withMLSClientProcessingOfWelcomeMessageFailsWith(exception: Exception) = apply {
            coEvery {
                mlsContext.processWelcomeMessage(any())
            }.throws(exception)
        }

        suspend fun withMLSClientProcessingOfWelcomeMessageReturnsSuccessfully(welcomeBundle: WelcomeBundle = WELCOME_BUNDLE) = apply {
            coEvery {
                mlsContext.processWelcomeMessage(any())
            }.returns(welcomeBundle)
        }

        suspend fun withCheckRevocationListResult() {
            coEvery {
                checkRevocationList.check(any(), any())
            }.returns(Either.Right(1uL))
        }

        suspend fun withRefillKeyPackagesReturning(result: RefillKeyPackagesResult) = apply {
            coEvery {
                refillKeyPackagesUseCase.invoke(any())
            }.returns(result)
        }

        suspend fun withJoinExistingMLSConversationReturning(result: Either<CoreFailure, Unit>) = apply {
            coEvery {
                joinExistingMLSConversation(any(), conversationId = any())
            }.returns(result)
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
