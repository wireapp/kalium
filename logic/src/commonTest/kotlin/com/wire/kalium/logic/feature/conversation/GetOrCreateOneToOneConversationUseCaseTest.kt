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
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs

class GetOrCreateOneToOneConversationUseCaseTest {

    @Mock
    private val conversationRepository = mock(classOf<ConversationRepository>())

    @Mock
    private val conversationGroupRepository = mock(classOf<ConversationGroupRepository>())

    private lateinit var getOrCreateOneToOneConversationUseCase: GetOrCreateOneToOneConversationUseCase

    @BeforeTest
    fun setUp() {
        getOrCreateOneToOneConversationUseCase = GetOrCreateOneToOneConversationUseCase(
            conversationRepository = conversationRepository,
            conversationGroupRepository = conversationGroupRepository
        )
    }

    @Test
    fun givenConversationDoesNotExist_whenCallingTheUseCase_ThenDoNotCreateAConversationButReturnExisting() = runTest {
        // given
        given(conversationRepository)
            .suspendFunction(conversationRepository::observeOneToOneConversationWithOtherUser)
            .whenInvokedWith(anything())
            .thenReturn(flowOf(Either.Right(CONVERSATION)))

        given(conversationRepository)
            .suspendFunction(conversationGroupRepository::createGroupConversation)
            .whenInvokedWith(anything(), anything(), anything())
            .thenReturn(Either.Right(CONVERSATION))
        // when
        val result = getOrCreateOneToOneConversationUseCase.invoke(USER_ID)
        // then
        assertIs<CreateConversationResult.Success>(result)

        verify(conversationGroupRepository)
            .suspendFunction(conversationGroupRepository::createGroupConversation)
            .with(anything(), anything(), anything())
            .wasNotInvoked()

        verify(conversationRepository)
            .suspendFunction(conversationRepository::observeOneToOneConversationWithOtherUser)
            .with(anything())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenConversationExist_whenCallingTheUseCase_ThenCreateAConversationAndReturn() = runTest {
        // given
        given(conversationRepository)
            .coroutine { observeOneToOneConversationWithOtherUser(USER_ID) }
            .then { flowOf(Either.Left(StorageFailure.DataNotFound)) }

        given(conversationGroupRepository)
            .suspendFunction(conversationGroupRepository::createGroupConversation)
            .whenInvokedWith(anything(), anything(), anything())
            .thenReturn(Either.Right(CONVERSATION))
        // when
        val result = getOrCreateOneToOneConversationUseCase.invoke(USER_ID)
        // then
        assertIs<CreateConversationResult.Success>(result)

        verify(conversationGroupRepository)
            .coroutine { createGroupConversation(usersList = MEMBER) }
            .wasInvoked(exactly = once)
    }

    private companion object {
        val USER_ID = UserId(value = "userId", domain = "domainId")
        val MEMBER = listOf(USER_ID)
        val CONVERSATION_ID = ConversationId(value = "userId", domain = "domainId")
        val CONVERSATION = Conversation(
            id = CONVERSATION_ID,
            name = null,
            type = Conversation.Type.ONE_ON_ONE,
            teamId = null,
            ProtocolInfo.Proteus,
            MutedConversationStatus.AllAllowed,
            null,
            null,
            null,
            lastReadDate = "2022-03-30T15:36:00.000Z",
            access = listOf(Conversation.Access.CODE, Conversation.Access.INVITE),
            accessRole = listOf(Conversation.AccessRole.NON_TEAM_MEMBER, Conversation.AccessRole.GUEST),
            creatorId = null,
            receiptMode = Conversation.ReceiptMode.DISABLED,
            messageTimer = null,
            userMessageTimer = null,
            archived = false,
            archivedDateTime = null,
            verificationStatus = Conversation.VerificationStatus.NOT_VERIFIED
        )
    }
}
