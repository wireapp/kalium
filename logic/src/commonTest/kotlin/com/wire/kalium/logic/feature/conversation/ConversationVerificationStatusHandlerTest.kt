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

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.ConversationVerificationStatus
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationVerificationStatusHandlerTest {

    @Test
    fun given_whenInvokingWithVerifiedStatus_thenInformedFlagSetFalse() = runTest {
        val conversation = TestConversation.MLS_CONVERSATION
        val (arrangement, handler) = Arrangement()
            .arrange()

        handler(conversation, ConversationVerificationStatus.VERIFIED)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::setInformedAboutDegradedMLSVerificationFlag)
            .with(eq(conversation.id), eq(false))
            .wasInvoked(once)
    }

    @Test
    fun given_whenInvokingWithNotVerifiedStatus_thenInformedFlagSetFalse() = runTest {
        val conversation = TestConversation.MLS_CONVERSATION
        val (arrangement, handler) = Arrangement()
            .arrange()

        handler(conversation, ConversationVerificationStatus.NOT_VERIFIED)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::setInformedAboutDegradedMLSVerificationFlag)
            .with(eq(conversation.id), eq(false))
            .wasInvoked(once)
    }

    @Test
    fun givenProteusConversation_whenInvokingWithDegradedStatus_thenInformedFlagNotSetFalse() = runTest {
        val conversation = TestConversation.CONVERSATION
        val (arrangement, handler) = Arrangement()
            .arrange()

        handler(conversation, ConversationVerificationStatus.DEGRADED)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::setInformedAboutDegradedMLSVerificationFlag)
            .with(eq(conversation.id), eq(false))
            .wasNotInvoked()
    }

    @Test
    fun givenMLSConversation_whenInvokingWithDegradedStatus_thenInformedFlagNotSetFalse() = runTest {
        val conversation = TestConversation.CONVERSATION
        val (arrangement, handler) = Arrangement()
            .withInformedAboutDegradedMLSVerification(Either.Right(true))
            .arrange()

        handler(conversation, ConversationVerificationStatus.DEGRADED)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::setInformedAboutDegradedMLSVerificationFlag)
            .with(eq(conversation.id), eq(false))
            .wasNotInvoked()
    }

    @Test
    fun givenMLSConversationAndInformedAboutDegraded_whenInvokingWithVerifiedStatus_thenInformedFlagSetTrue() = runTest {
        val conversation = TestConversation.CONVERSATION
        val (arrangement, handler) = Arrangement()
            .withInformedAboutDegradedMLSVerification(Either.Right(true))
            .arrange()

        handler(conversation, ConversationVerificationStatus.VERIFIED)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::setInformedAboutDegradedMLSVerificationFlag)
            .with(eq(conversation.id), eq(false))
            .wasInvoked(once)
    }

    @Test
    fun givenUserInformedAboutDegradedStatus_whenInvokingWithDegradedStatus_thenInformedFlagSetFalse() = runTest {
        val conversation = TestConversation.MLS_CONVERSATION
        val (arrangement, handler) = Arrangement()
            .withInformedAboutDegradedMLSVerification(Either.Right(true))
            .arrange()

        handler(conversation, ConversationVerificationStatus.DEGRADED)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::setInformedAboutDegradedMLSVerificationFlag)
            .with(eq(conversation.id), eq(false))
            .wasNotInvoked()
    }

    @Test
    fun givenFailureWhileGettingUserInformedAboutDegradedStatus_whenInvokingWithDegradedStatus_thenInformedFlagNotSetFalse() = runTest {
        val conversation = TestConversation.MLS_CONVERSATION
        val (arrangement, handler) = Arrangement()
            .withInformedAboutDegradedMLSVerification(Either.Left(StorageFailure.DataNotFound))
            .arrange()

        handler(conversation, ConversationVerificationStatus.DEGRADED)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::setInformedAboutDegradedMLSVerificationFlag)
            .with(eq(conversation.id), eq(false))
            .wasNotInvoked()
    }

    @Test
    fun givenUserIsNotInformedAboutDegradedStatus_whenInvokingWithDegradedStatus_thenInformedFlagSetTrueAndAddMessage() = runTest {
        val conversation = TestConversation.MLS_CONVERSATION
        val (arrangement, handler) = Arrangement()
            .withInformedAboutDegradedMLSVerification(Either.Right(false))
            .withPersistMessageResult(Either.Right(Unit))
            .arrange()

        handler(conversation, ConversationVerificationStatus.DEGRADED)

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
        val (arrangement, handler) = Arrangement()
            .withInformedAboutDegradedMLSVerification(Either.Right(false))
            .withPersistMessageResult(Either.Left(StorageFailure.DataNotFound))
            .arrange()

        handler(conversation, ConversationVerificationStatus.DEGRADED)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::setInformedAboutDegradedMLSVerificationFlag)
            .with(eq(conversation.id), eq(true))
            .wasNotInvoked()

        verify(arrangement.persistMessageUseCase)
            .suspendFunction(arrangement.persistMessageUseCase::invoke)
            .with(any())
            .wasInvoked(once)
    }

    private class Arrangement {

        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val persistMessageUseCase = mock(classOf<PersistMessageUseCase>())

        fun arrange() = this to ConversationVerificationStatusHandlerImpl(
            conversationRepository,
            persistMessageUseCase,
            TestUser.USER_ID
        )

        init {
            given(conversationRepository)
                .suspendFunction(conversationRepository::setInformedAboutDegradedMLSVerificationFlag)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withInformedAboutDegradedMLSVerification(isInformed: Either<StorageFailure, Boolean>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::isInformedAboutDegradedMLSVerification)
                .whenInvokedWith(any())
                .thenReturn(isInformed)
        }

        fun withPersistMessageResult(result: Either<StorageFailure, Unit>) = apply {
            given(persistMessageUseCase)
                .suspendFunction(persistMessageUseCase::invoke)
                .whenInvokedWith(any())
                .thenReturn(result)
        }
    }
}
