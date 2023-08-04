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
package com.wire.kalium.logic.util.arrangement.repository

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.matchers.Matcher
import io.mockative.mock

internal interface ConversationRepositoryArrangement {
    val conversationRepository: ConversationRepository
    fun withGetGroupConversationsWithMembersWithBothDomains(
        result: Either<CoreFailure, Map<ConversationId, List<UserId>>>,
        firstDomain: Matcher<String> = any(),
        secondDomain: Matcher<String> = any()
    )

    fun withGetOneOnOneConversationsWithFederatedMember(
        result: Either<CoreFailure, Map<ConversationId, UserId>>,
        domain: Matcher<String> = any()
    )

    fun withDeletingConversationSucceeding(conversationId: Matcher<ConversationId> = any())
    fun withDeletingConversationFailing(conversationId: Matcher<ConversationId> = any())
    fun withGetConversation(conversation: Conversation? = TestConversation.CONVERSATION)
    fun withUpdateProtocolLocally(result: Either<CoreFailure, Boolean>)
    fun withConversationsForUserIdReturning(result: Either<CoreFailure, List<Conversation>>)
    fun withFetchMlsOneToOneConversation(result: Either<CoreFailure, Conversation>)
}

internal open class ConversationRepositoryArrangementImpl : ConversationRepositoryArrangement {

    @Mock
    override val conversationRepository: ConversationRepository = mock(ConversationRepository::class)

    override fun withGetGroupConversationsWithMembersWithBothDomains(
        result: Either<CoreFailure, Map<ConversationId, List<UserId>>>,
        firstDomain: Matcher<String>,
        secondDomain: Matcher<String>,
    ) {
        given(conversationRepository)
            .suspendFunction(conversationRepository::getGroupConversationsWithMembersWithBothDomains)
            .whenInvokedWith(firstDomain, secondDomain)
            .thenReturn(result)
    }

    override fun withGetOneOnOneConversationsWithFederatedMember(
        result: Either<CoreFailure, Map<ConversationId, UserId>>,
        domain: Matcher<String>
    ) {
        given(conversationRepository)
            .suspendFunction(conversationRepository::getOneOnOneConversationsWithFederatedMembers)
            .whenInvokedWith(domain)
            .thenReturn(result)
    }

    override fun withDeletingConversationSucceeding(conversationId: Matcher<ConversationId>) {
        given(conversationRepository)
            .suspendFunction(conversationRepository::deleteConversation)
            .whenInvokedWith(conversationId)
            .thenReturn(Either.Right(Unit))
    }

    override fun withDeletingConversationFailing(conversationId: Matcher<ConversationId>) {
        given(conversationRepository)
            .suspendFunction(conversationRepository::deleteConversation)
            .whenInvokedWith(conversationId)
            .thenReturn(Either.Left(CoreFailure.Unknown(RuntimeException("some error"))))
    }

    override fun withGetConversation(conversation: Conversation?) {
        given(conversationRepository)
            .suspendFunction(conversationRepository::getConversationById)
            .whenInvokedWith(any())
            .thenReturn(conversation)
    }

    override fun withUpdateProtocolLocally(result: Either<CoreFailure, Boolean>) {
        given(conversationRepository)
            .suspendFunction(conversationRepository::updateProtocolLocally)
            .whenInvokedWith(any(), any())
            .thenReturn(result)
    }

    override fun withConversationsForUserIdReturning(result: Either<CoreFailure, List<Conversation>>) {
        given(conversationRepository)
            .suspendFunction(conversationRepository::getConversationsByUserId)
            .whenInvokedWith(any())
            .thenReturn(result)
    }

    override fun withFetchMlsOneToOneConversation(result: Either<CoreFailure, Conversation>) {
        given(conversationRepository)
            .suspendFunction(conversationRepository::fetchMlsOneToOneConversation)
            .whenInvokedWith(any())
            .thenReturn(result)
    }
}
