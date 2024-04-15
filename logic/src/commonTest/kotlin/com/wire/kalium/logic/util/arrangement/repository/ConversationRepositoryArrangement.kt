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
package com.wire.kalium.logic.util.arrangement.repository

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.mls.EpochChangesData
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.matchers.Matcher
import io.mockative.mock
import kotlinx.coroutines.flow.flowOf

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
    fun withSetInformedAboutDegradedMLSVerificationFlagResult(result: Either<StorageFailure, Unit> = Either.Right(Unit))
    fun withInformedAboutDegradedMLSVerification(isInformed: Either<StorageFailure, Boolean>): ConversationRepositoryArrangementImpl
    fun withConversationProtocolInfo(result: Either<StorageFailure, Conversation.ProtocolInfo>): ConversationRepositoryArrangementImpl
    fun withUpdateVerificationStatus(result: Either<StorageFailure, Unit>): ConversationRepositoryArrangementImpl
    fun withConversationDetailsByMLSGroupId(result: Either<StorageFailure, ConversationDetails>): ConversationRepositoryArrangementImpl
    fun withUpdateProtocolLocally(result: Either<CoreFailure, Boolean>)
    fun withConversationsForUserIdReturning(result: Either<CoreFailure, List<Conversation>>)
    fun withFetchMlsOneToOneConversation(result: Either<CoreFailure, Conversation>)
    fun withFetchConversation(result: Either<CoreFailure, Unit>)
    fun withObserveOneToOneConversationWithOtherUserReturning(result: Either<CoreFailure, Conversation>)

    fun withObserveConversationDetailsByIdReturning(vararg results: Either<StorageFailure, ConversationDetails>)

    fun withGetConversationIdsReturning(result: Either<StorageFailure, List<QualifiedID>>)

    fun withGetOneOnOneConversationsWithOtherUserReturning(result: Either<StorageFailure, List<QualifiedID>>)

    fun withGetConversationProtocolInfo(result: Either<StorageFailure, Conversation.ProtocolInfo>)

    fun withGetConversationByIdReturning(result: Conversation?)

    fun withFetchConversationIfUnknownFailingWith(coreFailure: CoreFailure) {
        given(conversationRepository)
            .suspendFunction(conversationRepository::fetchConversationIfUnknown)
            .whenInvokedWith(any())
            .thenReturn(Either.Left(coreFailure))
    }

    fun withFetchConversationIfUnknownSucceeding() {
        given(conversationRepository)
            .suspendFunction(conversationRepository::fetchConversationIfUnknown)
            .whenInvokedWith(any())
            .thenReturn(Either.Right(Unit))
    }

    fun withUpdateGroupStateReturning(result: Either<StorageFailure, Unit>) {
        given(conversationRepository)
            .suspendFunction(conversationRepository::updateConversationGroupState)
            .whenInvokedWith(any(), any())
            .thenReturn(result)
    }

    fun withUpdateConversationModifiedDate(result: Either<StorageFailure, Unit>) {
        given(conversationRepository)
            .suspendFunction(conversationRepository::updateConversationModifiedDate)
            .whenInvokedWith(any(), any())
            .thenReturn(result)
    }

    fun withSetDegradedConversationNotifiedFlag(result: Either<CoreFailure, Unit>)

    fun withSelectGroupStatusMembersNamesAndHandles(result: Either<StorageFailure, EpochChangesData>)
    fun withConversationDetailsByIdReturning(result: Either<StorageFailure, Conversation>)
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

    override fun withSetInformedAboutDegradedMLSVerificationFlagResult(result: Either<StorageFailure, Unit>) {
        given(conversationRepository)
            .suspendFunction(conversationRepository::setInformedAboutDegradedMLSVerificationFlag)
            .whenInvokedWith(any())
            .thenReturn(result)
    }

    override fun withInformedAboutDegradedMLSVerification(isInformed: Either<StorageFailure, Boolean>) = apply {
        given(conversationRepository)
            .suspendFunction(conversationRepository::isInformedAboutDegradedMLSVerification)
            .whenInvokedWith(any())
            .thenReturn(isInformed)
    }

    override fun withConversationProtocolInfo(result: Either<StorageFailure, Conversation.ProtocolInfo>) = apply {
        given(conversationRepository)
            .suspendFunction(conversationRepository::getConversationProtocolInfo)
            .whenInvokedWith(any())
            .thenReturn(result)
    }

    override fun withUpdateVerificationStatus(result: Either<StorageFailure, Unit>) = apply {
        given(conversationRepository)
            .suspendFunction(conversationRepository::updateMlsVerificationStatus)
            .whenInvokedWith(any())
            .thenReturn(result)
    }

    override fun withConversationDetailsByMLSGroupId(result: Either<StorageFailure, ConversationDetails>) = apply {
        given(conversationRepository)
            .suspendFunction(conversationRepository::getConversationDetailsByMLSGroupId)
            .whenInvokedWith(any())
            .thenReturn(result)
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

    override fun withFetchConversation(result: Either<CoreFailure, Unit>) {
        given(conversationRepository)
            .suspendFunction(conversationRepository::fetchConversation)
            .whenInvokedWith(any())
            .thenReturn(result)
    }

    override fun withObserveOneToOneConversationWithOtherUserReturning(result: Either<CoreFailure, Conversation>) {
        given(conversationRepository)
            .suspendFunction(conversationRepository::observeOneToOneConversationWithOtherUser)
            .whenInvokedWith(any())
            .thenReturn(flowOf(result))
    }

    override fun withObserveConversationDetailsByIdReturning(vararg results: Either<StorageFailure, ConversationDetails>) {
        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationDetailsById)
            .whenInvokedWith(any())
            .thenReturn(flowOf(*results))
    }

    override fun withGetConversationIdsReturning(result: Either<StorageFailure, List<QualifiedID>>) {
        given(conversationRepository)
            .suspendFunction(conversationRepository::getConversationIds)
            .whenInvokedWith(any())
            .thenReturn(result)
    }

    override fun withGetOneOnOneConversationsWithOtherUserReturning(result: Either<StorageFailure, List<QualifiedID>>) {
        given(conversationRepository)
            .suspendFunction(conversationRepository::getOneOnOneConversationsWithOtherUser)
            .whenInvokedWith(any())
            .thenReturn(result)
    }

    override fun withGetConversationProtocolInfo(result: Either<StorageFailure, Conversation.ProtocolInfo>) {
        given(conversationRepository)
            .suspendFunction(conversationRepository::getConversationProtocolInfo)
            .whenInvokedWith(any())
            .thenReturn(result)
    }

    override fun withGetConversationByIdReturning(result: Conversation?) {
        given(conversationRepository)
            .suspendFunction(conversationRepository::getConversationById)
            .whenInvokedWith(any())
            .thenReturn(result)
    }

    override fun withSetDegradedConversationNotifiedFlag(result: Either<CoreFailure, Unit>) {
        given(conversationRepository)
            .suspendFunction(conversationRepository::setDegradedConversationNotifiedFlag)
            .whenInvokedWith(any())
            .thenReturn(result)
    }

    override fun withSelectGroupStatusMembersNamesAndHandles(result: Either<StorageFailure, EpochChangesData>) {
        given(conversationRepository)
            .suspendFunction(conversationRepository::getGroupStatusMembersNamesAndHandles)
            .whenInvokedWith(any())
            .thenReturn(result)
    }

    override fun withConversationDetailsByIdReturning(result: Either<StorageFailure, Conversation>) {
        given(conversationRepository)
            .suspendFunction(conversationRepository::detailsById)
            .whenInvokedWith(any())
            .thenReturn(result)
    }
}
