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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationProtocolUpdateStatus
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.mls.EpochChangesData
import com.wire.kalium.logic.data.conversation.mls.NameAndHandle
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.util.ConversationPersistenceApi
import io.mockative.any
import io.mockative.coEvery
import io.mockative.eq
import io.mockative.fake.valueOf
import io.mockative.matchers.AnyMatcher
import io.mockative.matchers.Matcher
import io.mockative.matches
import io.mockative.mock
import kotlinx.coroutines.flow.flowOf

internal interface ConversationRepositoryArrangement {
    val conversationRepository: ConversationRepository
    suspend fun withGetGroupConversationsWithMembersWithBothDomains(
        result: Either<CoreFailure, Map<ConversationId, List<UserId>>>,
        firstDomain: Matcher<String> = AnyMatcher(valueOf()),
        secondDomain: Matcher<String> = AnyMatcher(valueOf())
    )

    suspend fun withGetOneOnOneConversationsWithFederatedMember(
        result: Either<CoreFailure, Map<ConversationId, UserId>>,
        domain: Matcher<String> = AnyMatcher(valueOf())
    )

    suspend fun withMarkConversationAsDeletedLocallySucceeding(conversationId: Matcher<ConversationId> = AnyMatcher(valueOf()))
    suspend fun withMarkConversationAsDeletedLocallyFailing(conversationId: Matcher<ConversationId> = AnyMatcher(valueOf()))
    suspend fun withDeletingConversationLocallySucceeding(conversationId: Matcher<ConversationId> = AnyMatcher(valueOf()))
    suspend fun withDeletingConversationLocallyFailing(conversationId: Matcher<ConversationId> = AnyMatcher(valueOf()))
    suspend fun withGetConversationByIdReturning(conversation: Conversation? = TestConversation.CONVERSATION)
    suspend fun withSetInformedAboutDegradedMLSVerificationFlagResult(result: Either<StorageFailure, Unit> = Either.Right(Unit))
    suspend fun withInformedAboutDegradedMLSVerification(isInformed: Either<StorageFailure, Boolean>): ConversationRepositoryArrangement
    suspend fun withConversationProtocolInfo(result: Either<StorageFailure, Conversation.ProtocolInfo>): ConversationRepositoryArrangement
    suspend fun withUpdateVerificationStatus(result: Either<StorageFailure, Unit>): ConversationRepositoryArrangement
    suspend fun withConversationByMLSGroupId(result: Either<StorageFailure, Conversation>): ConversationRepositoryArrangement
    suspend fun withUpdateProtocolLocally(result: Either<CoreFailure, ConversationProtocolUpdateStatus>)
    suspend fun withConversationsForUserIdReturning(result: Either<CoreFailure, List<Conversation>>)
    suspend fun withFetchMlsOneToOneConversation(result: Either<CoreFailure, ConversationResponse>)
    suspend fun withObserveOneToOneConversationWithOtherUserReturning(result: Either<CoreFailure, Conversation>)

    suspend fun withObserveConversationDetailsByIdReturning(vararg results: Either<StorageFailure, ConversationDetails>)

    suspend fun withGetConversationIdsReturning(result: Either<StorageFailure, List<QualifiedID>>)

    suspend fun withGetOneOnOneConversationsWithOtherUserReturning(result: Either<StorageFailure, List<QualifiedID>>)

    suspend fun withGetConversationProtocolInfo(result: Either<StorageFailure, Conversation.ProtocolInfo>)

    suspend fun withObserveByIdReturning(conversation: Conversation) {
        coEvery {
            conversationRepository.observeConversationById(eq(conversation.id))
        }.returns(flowOf(Either.Right(conversation)))
    }

    suspend fun withUpdateGroupStateReturning(result: Either<StorageFailure, Unit>) {
        coEvery {
            conversationRepository.updateConversationGroupState(any(), any())
        }.returns(result)
    }

    suspend fun withUpdateConversationModifiedDate(result: Either<StorageFailure, Unit>) {
        coEvery {
            conversationRepository.updateConversationModifiedDate(any(), any())
        }.returns(result)
    }

    suspend fun withSetDegradedConversationNotifiedFlag(result: Either<CoreFailure, Unit>)

    suspend fun withSelectGroupStatusMembersNamesAndHandles(result: Either<StorageFailure, EpochChangesData>)
    suspend fun withConversationDetailsByIdReturning(result: Either<StorageFailure, Conversation>)
    suspend fun withPersistMembers(result: Either<StorageFailure, Unit>)
    suspend fun withMembersNameAndHandle(result: Either<StorageFailure, Map<UserId, NameAndHandle>>)
    suspend fun withAddConversationToDeleteQueue()
    suspend fun withRemoveConversationToDeleteQueue()
    suspend fun withGetConversationsDeleteQueue(result: List<ConversationId>)
    suspend fun withClearContentSucceeding()
    suspend fun withGetConversationMembers(result: List<UserId>)
}

@OptIn(ConversationPersistenceApi::class)
internal open class ConversationRepositoryArrangementImpl : ConversationRepositoryArrangement {

    override val conversationRepository: ConversationRepository = mock(ConversationRepository::class)

    override suspend fun withGetGroupConversationsWithMembersWithBothDomains(
        result: Either<CoreFailure, Map<ConversationId, List<UserId>>>,
        firstDomain: Matcher<String>,
        secondDomain: Matcher<String>,
    ) {
        coEvery {
            conversationRepository.getGroupConversationsWithMembersWithBothDomains(
                matches { firstDomain.matches(it) },
                matches { secondDomain.matches(it) }
            )
        }.returns(result)
    }

    override suspend fun withGetOneOnOneConversationsWithFederatedMember(
        result: Either<CoreFailure, Map<ConversationId, UserId>>,
        domain: Matcher<String>
    ) {
        coEvery {
            conversationRepository.getOneOnOneConversationsWithFederatedMembers(
                matches { domain.matches(it) }
            )
        }.returns(result)
    }

    override suspend fun withMarkConversationAsDeletedLocallySucceeding(conversationId: Matcher<ConversationId>) {
        coEvery {
            conversationRepository.markConversationAsDeletedLocally(matches { conversationId.matches(it) })
        }.returns(Either.Right(true))
    }

    override suspend fun withMarkConversationAsDeletedLocallyFailing(conversationId: Matcher<ConversationId>) {
        coEvery {
            conversationRepository.markConversationAsDeletedLocally(matches { conversationId.matches(it) })
        }.returns(Either.Left(CoreFailure.Unknown(RuntimeException("some error"))))
    }

    override suspend fun withDeletingConversationLocallySucceeding(conversationId: Matcher<ConversationId>) {
        coEvery {
            conversationRepository.deleteConversationLocally(matches { conversationId.matches(it) })
        }.returns(Either.Right(true))
    }

    override suspend fun withDeletingConversationLocallyFailing(conversationId: Matcher<ConversationId>) {
        coEvery {
            conversationRepository.deleteConversationLocally(matches { conversationId.matches(it) })
        }.returns(Either.Left(CoreFailure.Unknown(RuntimeException("some error"))))
    }

    override suspend fun withSetInformedAboutDegradedMLSVerificationFlagResult(result: Either<StorageFailure, Unit>) {
        coEvery {
            conversationRepository.setInformedAboutDegradedMLSVerificationFlag(any(), any())
        }.returns(result)
    }

    override suspend fun withInformedAboutDegradedMLSVerification(isInformed: Either<StorageFailure, Boolean>) = apply {
        coEvery {
            conversationRepository.isInformedAboutDegradedMLSVerification(any())
        }.returns(isInformed)
    }

    override suspend fun withConversationProtocolInfo(result: Either<StorageFailure, Conversation.ProtocolInfo>) = apply {
        coEvery {
            conversationRepository.getConversationProtocolInfo(any())
        }.returns(result)
    }

    override suspend fun withUpdateVerificationStatus(result: Either<StorageFailure, Unit>) = apply {
        coEvery {
            conversationRepository.updateMlsVerificationStatus(any(), any())
        }.returns(result)
    }

    override suspend fun withConversationByMLSGroupId(result: Either<StorageFailure, Conversation>) = apply {
        coEvery {
            conversationRepository.getConversationByMLSGroupId(any())
        }.returns(result)
    }

    override suspend fun withUpdateProtocolLocally(result: Either<CoreFailure, ConversationProtocolUpdateStatus>) {
        coEvery {
            conversationRepository.updateProtocolLocally(any(), any())
        }.returns(result)
    }

    override suspend fun withConversationsForUserIdReturning(result: Either<CoreFailure, List<Conversation>>) {
        coEvery {
            conversationRepository.getConversationsByUserId(any())
        }.returns(result)
    }

    override suspend fun withFetchMlsOneToOneConversation(result: Either<CoreFailure, ConversationResponse>) {
        coEvery {
            conversationRepository.fetchMlsOneToOneConversation(any())
        }.returns(result)
    }

    override suspend fun withObserveOneToOneConversationWithOtherUserReturning(result: Either<CoreFailure, Conversation>) {
        coEvery {
            conversationRepository.observeOneToOneConversationWithOtherUser(any())
        }.returns(flowOf(result))
    }

    override suspend fun withObserveConversationDetailsByIdReturning(vararg results: Either<StorageFailure, ConversationDetails>) {
        coEvery {
            conversationRepository.observeConversationDetailsById(any())
        }.returns(flowOf(*results))
    }

    override suspend fun withGetConversationIdsReturning(result: Either<StorageFailure, List<QualifiedID>>) {
        coEvery {
            conversationRepository.getConversationIds(any(), any())
        }.returns(result)
    }

    override suspend fun withGetOneOnOneConversationsWithOtherUserReturning(result: Either<StorageFailure, List<QualifiedID>>) {
        coEvery {
            conversationRepository.getOneOnOneConversationsWithOtherUser(any(), any())
        }.returns(result)
    }

    override suspend fun withGetConversationProtocolInfo(result: Either<StorageFailure, Conversation.ProtocolInfo>) {
        coEvery {
            conversationRepository.getConversationProtocolInfo(any())
        }.returns(result)
    }

    override suspend fun withGetConversationByIdReturning(conversation: Conversation?) {
        coEvery {
            conversationRepository.getConversationById(any())
        }.returns(
            if (conversation != null) Either.Right(conversation)
            else Either.Left(StorageFailure.DataNotFound)
        )
    }

    override suspend fun withSetDegradedConversationNotifiedFlag(result: Either<CoreFailure, Unit>) {
        coEvery {
            conversationRepository.setDegradedConversationNotifiedFlag(any(), any())
        }.returns(result)
    }

    override suspend fun withSelectGroupStatusMembersNamesAndHandles(result: Either<StorageFailure, EpochChangesData>) {
        coEvery {
            conversationRepository.getGroupStatusMembersNamesAndHandles(any())
        }.returns(result)
    }

    override suspend fun withConversationDetailsByIdReturning(result: Either<StorageFailure, Conversation>) {
        coEvery {
            conversationRepository.getConversationById(any())
        }.returns(result)
    }

    override suspend fun withPersistMembers(result: Either<StorageFailure, Unit>) {
        coEvery { conversationRepository.persistMembers(any(), any()) }.returns(result)
    }

    override suspend fun withMembersNameAndHandle(result: Either<StorageFailure, Map<UserId, NameAndHandle>>) {
        coEvery { conversationRepository.selectMembersNameAndHandle(any()) }.returns(result)
    }

    override suspend fun withAddConversationToDeleteQueue() {
        coEvery { conversationRepository.addConversationToDeleteQueue(any()) }.returns(Unit)
    }

    override suspend fun withRemoveConversationToDeleteQueue() {
        coEvery { conversationRepository.removeConversationFromDeleteQueue(any()) }.returns(Unit)
    }

    override suspend fun withGetConversationsDeleteQueue(result: List<ConversationId>) {
        coEvery { conversationRepository.getConversationsDeleteQueue() }.returns(result)
    }

    override suspend fun withClearContentSucceeding() {
        coEvery { conversationRepository.clearContent(any()) }.returns(Either.Right(Unit))
    }

    override suspend fun withGetConversationMembers(result: List<UserId>) {
        coEvery { conversationRepository.getConversationMembers(any()) }.returns(Either.Right(result))
    }
}
