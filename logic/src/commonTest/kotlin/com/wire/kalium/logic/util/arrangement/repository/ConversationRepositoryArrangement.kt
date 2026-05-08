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
import com.wire.kalium.logic.data.id.GroupID
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
import kotlinx.datetime.Instant

private val MOCKATIVE_CONVERSATION_ID = ConversationId("mockative-conversation", "mockative.test")
private val MOCKATIVE_USER_ID = UserId("mockative-user", "mockative.test")
private val MOCKATIVE_GROUP_ID = GroupID("mockative-group")
private val MOCKATIVE_INSTANT = Instant.fromEpochMilliseconds(0)

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

    suspend fun withSetConversationDeletedLocallySucceeding(conversationId: Matcher<ConversationId> = AnyMatcher(MOCKATIVE_CONVERSATION_ID))
    suspend fun withSetConversationDeletedLocallyFailing(conversationId: Matcher<ConversationId> = AnyMatcher(MOCKATIVE_CONVERSATION_ID))
    suspend fun withDeletingConversationLocallySucceeding(conversationId: Matcher<ConversationId> = AnyMatcher(MOCKATIVE_CONVERSATION_ID))
    suspend fun withDeletingConversationLocallyFailing(conversationId: Matcher<ConversationId> = AnyMatcher(MOCKATIVE_CONVERSATION_ID))
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
            conversationRepository.updateConversationGroupState(
                any(MOCKATIVE_GROUP_ID),
                any(Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_JOIN)
            )
        }.returns(result)
    }

    suspend fun withUpdateConversationModifiedDate(result: Either<StorageFailure, Unit>) {
        coEvery {
            conversationRepository.updateConversationModifiedDate(any(MOCKATIVE_CONVERSATION_ID), any(MOCKATIVE_INSTANT))
        }.returns(result)
    }

    suspend fun withSetDegradedConversationNotifiedFlag(result: Either<CoreFailure, Unit>)

    suspend fun withSelectGroupStatusMembersNamesAndHandles(result: Either<StorageFailure, EpochChangesData>)
    suspend fun withConversationDetailsByIdReturning(result: Either<StorageFailure, Conversation>)
    suspend fun withPersistMembers(result: Either<StorageFailure, Unit>)
    suspend fun withMembersNameAndHandle(result: Either<StorageFailure, Map<UserId, NameAndHandle>>)
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

    override suspend fun withSetConversationDeletedLocallySucceeding(conversationId: Matcher<ConversationId>) {
        coEvery {
            conversationRepository.setConversationDeletedLocally(matches(MOCKATIVE_CONVERSATION_ID) { conversationId.matches(it) }, any())
        }.returns(Either.Right(Unit))
    }

    override suspend fun withSetConversationDeletedLocallyFailing(conversationId: Matcher<ConversationId>) {
        coEvery {
            conversationRepository.setConversationDeletedLocally(matches(MOCKATIVE_CONVERSATION_ID) { conversationId.matches(it) }, any())
        }.returns(Either.Left(CoreFailure.Unknown(RuntimeException("some error"))))
    }

    override suspend fun withDeletingConversationLocallySucceeding(conversationId: Matcher<ConversationId>) {
        coEvery {
            conversationRepository.deleteConversationLocally(matches(MOCKATIVE_CONVERSATION_ID) { conversationId.matches(it) })
        }.returns(Either.Right(true))
    }

    override suspend fun withDeletingConversationLocallyFailing(conversationId: Matcher<ConversationId>) {
        coEvery {
            conversationRepository.deleteConversationLocally(matches(MOCKATIVE_CONVERSATION_ID) { conversationId.matches(it) })
        }.returns(Either.Left(CoreFailure.Unknown(RuntimeException("some error"))))
    }

    override suspend fun withSetInformedAboutDegradedMLSVerificationFlagResult(result: Either<StorageFailure, Unit>) {
        coEvery {
            conversationRepository.setInformedAboutDegradedMLSVerificationFlag(any(MOCKATIVE_CONVERSATION_ID), any())
        }.returns(result)
    }

    override suspend fun withInformedAboutDegradedMLSVerification(isInformed: Either<StorageFailure, Boolean>) = apply {
        coEvery {
            conversationRepository.isInformedAboutDegradedMLSVerification(any(MOCKATIVE_CONVERSATION_ID))
        }.returns(isInformed)
    }

    override suspend fun withConversationProtocolInfo(result: Either<StorageFailure, Conversation.ProtocolInfo>) = apply {
        coEvery {
            conversationRepository.getConversationProtocolInfo(any(MOCKATIVE_CONVERSATION_ID))
        }.returns(result)
    }

    override suspend fun withUpdateVerificationStatus(result: Either<StorageFailure, Unit>) = apply {
        coEvery {
            conversationRepository.updateMlsVerificationStatus(any(Conversation.VerificationStatus.NOT_VERIFIED), any(MOCKATIVE_CONVERSATION_ID))
        }.returns(result)
    }

    override suspend fun withConversationByMLSGroupId(result: Either<StorageFailure, Conversation>) = apply {
        coEvery {
            conversationRepository.getConversationByMLSGroupId(any(MOCKATIVE_GROUP_ID))
        }.returns(result)
    }

    override suspend fun withUpdateProtocolLocally(result: Either<CoreFailure, ConversationProtocolUpdateStatus>) {
        coEvery {
            conversationRepository.updateProtocolLocally(any(MOCKATIVE_CONVERSATION_ID), any(Conversation.Protocol.PROTEUS))
        }.returns(result)
    }

    override suspend fun withConversationsForUserIdReturning(result: Either<CoreFailure, List<Conversation>>) {
        coEvery {
            conversationRepository.getConversationsByUserId(any(MOCKATIVE_USER_ID))
        }.returns(result)
    }

    override suspend fun withFetchMlsOneToOneConversation(result: Either<CoreFailure, ConversationResponse>) {
        coEvery {
            conversationRepository.fetchMlsOneToOneConversation(any(MOCKATIVE_USER_ID))
        }.returns(result)
    }

    override suspend fun withObserveOneToOneConversationWithOtherUserReturning(result: Either<CoreFailure, Conversation>) {
        coEvery {
            conversationRepository.observeOneToOneConversationWithOtherUser(any(MOCKATIVE_USER_ID))
        }.returns(flowOf(result))
    }

    override suspend fun withObserveConversationDetailsByIdReturning(vararg results: Either<StorageFailure, ConversationDetails>) {
        coEvery {
            conversationRepository.observeConversationDetailsById(any(MOCKATIVE_CONVERSATION_ID))
        }.returns(flowOf(*results))
    }

    override suspend fun withGetConversationIdsReturning(result: Either<StorageFailure, List<QualifiedID>>) {
        coEvery {
            conversationRepository.getConversationIds(any(TestConversation.CONVERSATION.type), any(Conversation.Protocol.PROTEUS))
        }.returns(result)
    }

    override suspend fun withGetOneOnOneConversationsWithOtherUserReturning(result: Either<StorageFailure, List<QualifiedID>>) {
        coEvery {
            conversationRepository.getOneOnOneConversationsWithOtherUser(any(MOCKATIVE_USER_ID), any(Conversation.Protocol.PROTEUS))
        }.returns(result)
    }

    override suspend fun withGetConversationProtocolInfo(result: Either<StorageFailure, Conversation.ProtocolInfo>) {
        coEvery {
            conversationRepository.getConversationProtocolInfo(any(MOCKATIVE_CONVERSATION_ID))
        }.returns(result)
    }

    override suspend fun withGetConversationByIdReturning(conversation: Conversation?) {
        coEvery {
            conversationRepository.getConversationById(any(MOCKATIVE_CONVERSATION_ID))
        }.returns(
            if (conversation != null) Either.Right(conversation)
            else Either.Left(StorageFailure.DataNotFound)
        )
    }

    override suspend fun withSetDegradedConversationNotifiedFlag(result: Either<CoreFailure, Unit>) {
        coEvery {
            conversationRepository.setDegradedConversationNotifiedFlag(any(MOCKATIVE_CONVERSATION_ID), any())
        }.returns(result)
    }

    override suspend fun withSelectGroupStatusMembersNamesAndHandles(result: Either<StorageFailure, EpochChangesData>) {
        coEvery {
            conversationRepository.getGroupStatusMembersNamesAndHandles(any(MOCKATIVE_GROUP_ID))
        }.returns(result)
    }

    override suspend fun withConversationDetailsByIdReturning(result: Either<StorageFailure, Conversation>) {
        coEvery {
            conversationRepository.getConversationById(any(MOCKATIVE_CONVERSATION_ID))
        }.returns(result)
    }

    override suspend fun withPersistMembers(result: Either<StorageFailure, Unit>) {
        coEvery { conversationRepository.persistMembers(any(emptyList()), any(MOCKATIVE_CONVERSATION_ID)) }.returns(result)
    }

    override suspend fun withMembersNameAndHandle(result: Either<StorageFailure, Map<UserId, NameAndHandle>>) {
        coEvery { conversationRepository.selectMembersNameAndHandle(any(MOCKATIVE_CONVERSATION_ID)) }.returns(result)
    }

    override suspend fun withClearContentSucceeding() {
        coEvery { conversationRepository.clearContent(any(MOCKATIVE_CONVERSATION_ID)) }.returns(Either.Right(Unit))
    }

    override suspend fun withGetConversationMembers(result: List<UserId>) {
        coEvery { conversationRepository.getConversationMembers(any(MOCKATIVE_CONVERSATION_ID)) }.returns(Either.Right(result))
    }
}
