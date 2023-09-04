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
package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.MLSGroupId
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import io.ktor.util.encodeBase64
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Ignore
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MLSWelcomeEventHandlerTest {

    @Test
    fun givenMLSClientFailsProcessingOfWelcomeMessageFails_thenShouldNotMarkConversationAsEstablished() = runTest {
        val exception = RuntimeException()

        val (arrangement, mlsWelcomeEventHandler) = Arrangement()
            .withMLSClientProcessingOfWelcomeMessageFailsWith(exception)
            .arrange()

        // TODO: make sure failure is propagated
        //       needs refactoring of EventReceiver
        mlsWelcomeEventHandler.handle(WELCOME_EVENT)

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::updateConversationGroupState)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenConversationFetchFails_thenShouldNotMarkConversationAsEstablished() = runTest {
        val failure = CoreFailure.Unknown(null)
        val (arrangement, mlsWelcomeEventHandler) = Arrangement()
            .withMLSClientProcessingOfWelcomeMessageReturnsSuccessfully(MLS_GROUP_ID)
            .withFetchConversationIfUnknownFailingWith(failure)
            .arrange()

        // TODO: make sure failure is propagated
        //       needs refactoring of EventReceiver
        mlsWelcomeEventHandler.handle(WELCOME_EVENT)

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::updateConversationGroupState)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenProcessingOfWelcomeAndConversationFetchSucceed_thenShouldMarkConversationAsEstablished() = runTest {
        val (arrangement, mlsWelcomeEventHandler) = Arrangement()
            .withMLSClientProcessingOfWelcomeMessageReturnsSuccessfully(MLS_GROUP_ID)
            .withFetchConversationIfUnknownSucceeding()
            .withUpdateGroupStateSucceeding()
            .arrange()

        // TODO: make sure failure is propagated
        //       needs refactoring of EventReceiver
        mlsWelcomeEventHandler.handle(WELCOME_EVENT)

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::updateConversationGroupState)
            .with(eq(ConversationEntity.GroupState.ESTABLISHED), eq(MLS_GROUP_ID))
            .wasInvoked(exactly = once)
    }

    // TODO: Implement this test once event handler is refactored
    @Ignore
    @Test
    fun givenUpdateGroupStateFails_thenShouldPropagateError() = runTest {
        val (_, mlsWelcomeEventHandler) = Arrangement()
            .withMLSClientProcessingOfWelcomeMessageReturnsSuccessfully(MLS_GROUP_ID)
            .withFetchConversationIfUnknownSucceeding()
            .withUpdateGroupStateFailingWith(RuntimeException())
            .arrange()

        mlsWelcomeEventHandler.handle(WELCOME_EVENT)
    }

    // TODO: Implement this test once event handler is refactored
    @Ignore
    @Test
    fun givenEverythingSucceeds_thenShouldPropagateSuccess() = runTest {
        val (_, mlsWelcomeEventHandler) = Arrangement()
            .withMLSClientProcessingOfWelcomeMessageReturnsSuccessfully(MLS_GROUP_ID)
            .withFetchConversationIfUnknownSucceeding()
            .withUpdateGroupStateSucceeding()
            .arrange()

        mlsWelcomeEventHandler.handle(WELCOME_EVENT)
    }

    private class Arrangement {
        @Mock
        val mlsClient: MLSClient = mock(classOf<MLSClient>())

        @Mock
        val mlsClientProvider: MLSClientProvider = mock(classOf<MLSClientProvider>())

        @Mock
        val conversationDAO: ConversationDAO = mock(classOf<ConversationDAO>())

        @Mock
        val conversationRepository: ConversationRepository = mock(classOf<ConversationRepository>())

        init {
            withMLSClientProviderReturningMLSClient()
        }

        fun withMLSClientProviderReturningMLSClient() = apply {
            given(mlsClientProvider)
                .suspendFunction(mlsClientProvider::getMLSClient)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(mlsClient))
        }

        fun withMLSClientProcessingOfWelcomeMessageFailsWith(exception: Exception) = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::processWelcomeMessage)
                .whenInvokedWith(any())
                .thenThrow(exception)
        }

        fun withMLSClientProcessingOfWelcomeMessageReturnsSuccessfully(mlsGroupId: MLSGroupId) = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::processWelcomeMessage)
                .whenInvokedWith(any())
                .thenReturn(mlsGroupId)
        }

        fun withFetchConversationIfUnknownFailingWith(coreFailure: CoreFailure) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::fetchConversationIfUnknown)
                .whenInvokedWith(any())
                .thenReturn(Either.Left(coreFailure))
        }

        fun withFetchConversationIfUnknownSucceeding() = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::fetchConversationIfUnknown)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withUpdateGroupStateFailingWith(exception: Exception) = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::updateConversationGroupState)
                .whenInvokedWith(any(), any())
                .thenThrow(exception)
        }

        fun withUpdateGroupStateSucceeding() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::updateConversationGroupState)
                .whenInvokedWith(any(), any())
                .thenReturn(Unit)
        }

        fun arrange() = this to MLSWelcomeEventHandlerImpl(
            mlsClientProvider = mlsClientProvider,
            conversationDAO = conversationDAO,
            conversationRepository = conversationRepository
        )
    }

    private companion object {
        const val MLS_GROUP_ID: MLSGroupId = "test-mlsGroupId"
        val CONVERSATION_ID = TestConversation.ID
        val WELCOME = "welcome".encodeToByteArray()
        val WELCOME_EVENT = Event.Conversation.MLSWelcome(
            "eventId",
            CONVERSATION_ID,
            false,
            TestUser.USER_ID,
            WELCOME.encodeBase64(),
            timestampIso = "2022-03-30T15:36:00.000Z"
        )
    }
}
