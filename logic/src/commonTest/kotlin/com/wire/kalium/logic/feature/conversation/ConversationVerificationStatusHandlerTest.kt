/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.MLSConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.MLSConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.usecase.PersistMessageUseCaseArrangement
import com.wire.kalium.logic.util.arrangement.usecase.PersistMessageUseCaseArrangementImpl
import io.mockative.any
import io.mockative.eq
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ConversationVerificationStatusHandlerTest {

    @Test
    fun givenVerifiedStatus_whenInvoking_thenInformedFlagSetFalse() = runTest {
        val conversation = TestConversation.MLS_CONVERSATION
        val (arrangement, handler) = arrange {
            withConversationProtocolInfo(Either.Right(conversation.protocol))
            withConversationVerificationStatus(Either.Right(Conversation.VerificationStatus.VERIFIED))
            withObserveEpochChanges(flowOf(TestConversation.GROUP_ID))
            withMLSConversationVerificationStatus(Either.Right(Conversation.VerificationStatus.VERIFIED))
        }

        handler(conversation.id, false).test {
            awaitItem()
            awaitComplete()
        }

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::setInformedAboutDegradedMLSVerificationFlag)
            .with(eq(conversation.id), eq(false))
            .wasInvoked(once)
    }

    @Test
    fun givenNotVerifiedStatus_whenInvoking_thenInformedFlagSetFalse() = runTest {
        val conversation = TestConversation.MLS_CONVERSATION
        val protocolInfo = TestConversation.MLS_PROTOCOL_INFO
        val (arrangement, handler) = arrange {
            withConversationProtocolInfo(Either.Right(protocolInfo))
            withConversationVerificationStatus(Either.Right(Conversation.VerificationStatus.NOT_VERIFIED))
            withInformedAboutDegradedMLSVerification(Either.Right(false))
            withObserveEpochChanges(flowOf(TestConversation.GROUP_ID))
            withMLSConversationVerificationStatus(Either.Right(Conversation.VerificationStatus.NOT_VERIFIED))
        }

        handler(conversation.id, false).test {
            awaitItem()
            awaitComplete()
        }

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::setInformedAboutDegradedMLSVerificationFlag)
            .with(eq(conversation.id), eq(false))
            .wasInvoked(once)
    }

    @Test
    fun givenProteusConversation_whenInvokingWithNotVerifiedStatus_thenInformedFlagSetFalse() = runTest {
        val conversation = TestConversation.CONVERSATION
        val (arrangement, handler) = arrange {
            withConversationProtocolInfo(Either.Right(conversation.protocol))
            withConversationVerificationStatus(Either.Right(Conversation.VerificationStatus.NOT_VERIFIED))
        }

        handler(conversation.id, false).test {
            awaitItem()
            awaitComplete()
        }

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::setInformedAboutDegradedMLSVerificationFlag)
            .with(eq(conversation.id), eq(false))
            .wasInvoked(once)
    }

    @Test
    fun givenMLSConversationWithNotVerifiedStatus_whenInvokingWithNotVerifiedStatus_thenInformedFlagNotSetFalse() = runTest {
        val conversation = TestConversation.MLS_CONVERSATION
        val (arrangement, handler) = arrange {
            withInformedAboutDegradedMLSVerification(Either.Right(true))
            withConversationProtocolInfo(Either.Right(conversation.protocol))
            withConversationVerificationStatus(Either.Right(Conversation.VerificationStatus.VERIFIED))
            withObserveEpochChanges(flowOf(TestConversation.GROUP_ID))
            withMLSConversationVerificationStatus(Either.Right(Conversation.VerificationStatus.NOT_VERIFIED))
        }

        handler(conversation.id, false).test {
            awaitItem()
            awaitComplete()
        }

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::setInformedAboutDegradedMLSVerificationFlag)
            .with(eq(conversation.id), eq(false))
            .wasNotInvoked()
    }

    @Test
    fun givenMLSConversationAndInformedAboutDegraded_whenInvokingWithVerifiedStatus_thenInformedFlagSetTrue() = runTest {
        val conversation = TestConversation.MLS_CONVERSATION.copy(verificationStatus = Conversation.VerificationStatus.VERIFIED)
        val (arrangement, handler) = arrange {
            withInformedAboutDegradedMLSVerification(Either.Right(true))
            withConversationProtocolInfo(Either.Right(conversation.protocol))
            withConversationVerificationStatus(Either.Right(conversation.verificationStatus))
            withObserveEpochChanges(flowOf(TestConversation.GROUP_ID))
            withMLSConversationVerificationStatus(Either.Right(Conversation.VerificationStatus.VERIFIED))
        }

        handler(conversation.id, false).test {
            awaitItem()
            awaitComplete()
        }

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::setInformedAboutDegradedMLSVerificationFlag)
            .with(eq(conversation.id), eq(false))
            .wasInvoked(once)
    }

    @Test
    fun givenUserInformedAboutDegradedStatus_whenInvokingWithNotVerifiedStatus_thenInformedFlagSetFalse() = runTest {
        val conversation = TestConversation.MLS_CONVERSATION
        val protocol = TestConversation.MLS_PROTOCOL_INFO
        val (arrangement, handler) = arrange {
            withInformedAboutDegradedMLSVerification(Either.Right(true))
            withConversationProtocolInfo(Either.Right(protocol))
            withConversationVerificationStatus(Either.Right(Conversation.VerificationStatus.DEGRADED))
            withObserveEpochChanges(flowOf(TestConversation.GROUP_ID))
            withMLSConversationVerificationStatus(Either.Right(Conversation.VerificationStatus.NOT_VERIFIED))
        }

        handler(conversation.id, false).test {
            awaitItem()
            awaitComplete()
        }

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::setInformedAboutDegradedMLSVerificationFlag)
            .with(eq(conversation.id), eq(false))
            .wasNotInvoked()
    }

    @Test
    fun givenFailureWhileGettingUserInformedAboutDegradedStatus_whenInvokingWithDegradedStatus_thenInformedFlagNotSetFalse() = runTest {
        val conversation = TestConversation.MLS_CONVERSATION
        val (arrangement, handler) = arrange {
            withInformedAboutDegradedMLSVerification(Either.Left(StorageFailure.DataNotFound))
            withConversationProtocolInfo(Either.Right(conversation.protocol))
            withConversationVerificationStatus(Either.Right(Conversation.VerificationStatus.VERIFIED))
            withObserveEpochChanges(flowOf(TestConversation.GROUP_ID))
            withMLSConversationVerificationStatus(Either.Right(Conversation.VerificationStatus.NOT_VERIFIED))
        }

        handler(conversation.id, false).test {
            awaitItem()
            awaitComplete()
        }

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::setInformedAboutDegradedMLSVerificationFlag)
            .with(eq(conversation.id), eq(false))
            .wasNotInvoked()
    }

    @Test
    fun givenUserIsNotInformedAboutDegradedStatus_whenInvokingWithDegradedStatus_thenInformedFlagSetTrueAndAddMessage() = runTest {
        val conversation = TestConversation.MLS_CONVERSATION
        val (arrangement, handler) = arrange {
            withInformedAboutDegradedMLSVerification(Either.Right(false))
            withPersistingMessage(Either.Right(Unit))
            withConversationProtocolInfo(Either.Right(conversation.protocol))
            withConversationVerificationStatus(Either.Right(Conversation.VerificationStatus.VERIFIED))
            withObserveEpochChanges(flowOf(TestConversation.GROUP_ID))
            withMLSConversationVerificationStatus(Either.Right(Conversation.VerificationStatus.NOT_VERIFIED))
        }

        handler(conversation.id, false).test {
            awaitItem()
            awaitComplete()
        }

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::setInformedAboutDegradedMLSVerificationFlag)
            .with(eq(conversation.id), eq(true))
            .wasInvoked(once)

        verify(arrangement.persistMessageUseCase)
            .suspendFunction(arrangement.persistMessageUseCase::invoke)
            .with(any())
            .wasInvoked(once)
    }

    @Test
    fun givenUserIsNotInformedAboutDegradedStatus_whenFailureWhilePersistMessage_thenInformedFlagNotSetTrue() = runTest {
        val conversation = TestConversation.MLS_CONVERSATION
        val (arrangement, handler) = arrange {
            withInformedAboutDegradedMLSVerification(Either.Right(false))
            withPersistingMessage(Either.Left(StorageFailure.DataNotFound))
            withConversationProtocolInfo(Either.Right(conversation.protocol))
            withConversationVerificationStatus(Either.Right(Conversation.VerificationStatus.VERIFIED))
            withObserveEpochChanges(flowOf(TestConversation.GROUP_ID))
            withMLSConversationVerificationStatus(Either.Right(Conversation.VerificationStatus.NOT_VERIFIED))
        }

        handler(conversation.id, false).test {
            awaitItem()
            awaitComplete()
        }

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::setInformedAboutDegradedMLSVerificationFlag)
            .with(eq(conversation.id), eq(true))
            .wasNotInvoked()

        verify(arrangement.persistMessageUseCase)
            .suspendFunction(arrangement.persistMessageUseCase::invoke)
            .with(any())
            .wasInvoked(once)
    }

    @Test
    fun givenCurrentVerificationStatusIsVerified_whenNewNotVerifiedStatusComes_thenDegradedStatusSaved() = runTest {
        val conversation = TestConversation.MLS_CONVERSATION
        val (arrangement, handler) = arrange {
            withInformedAboutDegradedMLSVerification(Either.Right(false))
            withPersistingMessage(Either.Left(StorageFailure.DataNotFound))
            withConversationProtocolInfo(Either.Right(conversation.protocol))
            withConversationVerificationStatus(Either.Right(Conversation.VerificationStatus.VERIFIED))
            withObserveEpochChanges(flowOf(TestConversation.GROUP_ID))
            withMLSConversationVerificationStatus(Either.Right(Conversation.VerificationStatus.NOT_VERIFIED))
        }

        handler(conversation.id, false).test {
            awaitItem()
            awaitComplete()
        }

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateVerificationStatus)
            .with(eq(Conversation.VerificationStatus.DEGRADED), eq(conversation.id))
            .wasInvoked(once)
    }

    @Test
    fun givenCurrentVerificationStatusIsVerified_whenNewVerifiedStatusComes_thenNothingUpdated() = runTest {
        val conversation = TestConversation.MLS_CONVERSATION
        val (arrangement, handler) = arrange {
            withInformedAboutDegradedMLSVerification(Either.Right(false))
            withPersistingMessage(Either.Left(StorageFailure.DataNotFound))
            withConversationProtocolInfo(Either.Right(conversation.protocol))
            withConversationVerificationStatus(Either.Right(Conversation.VerificationStatus.VERIFIED))
            withObserveEpochChanges(flowOf(TestConversation.GROUP_ID))
            withMLSConversationVerificationStatus(Either.Right(Conversation.VerificationStatus.VERIFIED))
        }

        handler(conversation.id, false).test {
            awaitItem()
            awaitComplete()
        }

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateVerificationStatus)
            .with(any(), eq(conversation.id))
            .wasNotInvoked()
    }

    @Test
    fun givenCurrentVerificationStatusIsDegraded_whenNewNotVerifiedStatusComes_thenDegradedStatusSaved() = runTest {
        val conversation = TestConversation.MLS_CONVERSATION
        val protocolInfo = TestConversation.MLS_PROTOCOL_INFO
        val (arrangement, handler) = arrange {
            withInformedAboutDegradedMLSVerification(Either.Right(false))
            withPersistingMessage(Either.Left(StorageFailure.DataNotFound))
            withConversationProtocolInfo(Either.Right(protocolInfo))
            withConversationVerificationStatus(Either.Right(Conversation.VerificationStatus.DEGRADED))
            withObserveEpochChanges(flowOf(TestConversation.GROUP_ID))
            withMLSConversationVerificationStatus(Either.Right(Conversation.VerificationStatus.NOT_VERIFIED))
        }

        handler(conversation.id, false).test {
            awaitItem()
            awaitComplete()
        }

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateVerificationStatus)
            .with(any(), eq(conversation.id))
            .wasNotInvoked()
    }

    private fun arrange(block: Arrangement.() -> Unit) = Arrangement(block).arrange()

    private class Arrangement(
        private val block: Arrangement.() -> Unit
    ) : ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl(),
        PersistMessageUseCaseArrangement by PersistMessageUseCaseArrangementImpl(),
        MLSConversationRepositoryArrangement by MLSConversationRepositoryArrangementImpl() {


        init {
            withSetInformedAboutDegradedMLSVerificationFlagResult()
            withUpdateVerificationStatus(Either.Right(Unit))
        }

        fun arrange() = block().run {
            this@Arrangement to ConversationVerificationStatusHandlerImpl(
                conversationRepository = conversationRepository,
                persistMessage = persistMessageUseCase,
                mlsConversationRepository = mlsConversationRepository,
                selfUserId = TestUser.USER_ID
            )
        }
    }
}
