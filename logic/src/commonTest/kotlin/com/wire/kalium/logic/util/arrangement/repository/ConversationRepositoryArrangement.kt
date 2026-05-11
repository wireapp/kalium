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
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matches
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.mock
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
        firstDomain: (String) -> Boolean = { true },
        secondDomain: (String) -> Boolean = { true }
    )

    suspend fun withGetOneOnOneConversationsWithFederatedMember(
        result: Either<CoreFailure, Map<ConversationId, UserId>>,
        domain: (String) -> Boolean = { true }
    )

    suspend fun withSetConversationDeletedLocallySucceeding(conversationId: (ConversationId) -> Boolean = { true })
    suspend fun withSetConversationDeletedLocallyFailing(conversationId: (ConversationId) -> Boolean = { true })
    suspend fun withDeletingConversationLocallySucceeding(conversationId: (ConversationId) -> Boolean = { true })
    suspend fun withDeletingConversationLocallyFailing(conversationId: (ConversationId) -> Boolean = { true })
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
        everySuspend {
            conversationRepository.observeConversationById(eq(conversation.id))
        }.returns(flowOf(Either.Right(conversation)))
    }

    suspend fun withUpdateGroupStateReturning(result: Either<StorageFailure, Unit>) {
        everySuspend {
            conversationRepository.updateConversationGroupState(
                any(),
                any()
            )
        }.returns(result)
    }

    suspend fun withUpdateConversationModifiedDate(result: Either<StorageFailure, Unit>) {
        everySuspend {
            conversationRepository.updateConversationModifiedDate(any(), any())
        }.returns(result)
    }

    suspend fun withSetDegradedConversationNotifiedFlag(result: Either<CoreFailure, Unit>)

    suspend fun withSelectGroupStatusMembersNamesAndHandles(result: Either<StorageFailure, EpochChangesData>)
    suspend fun withConversationDetailsByIdReturning(result: Either<StorageFailure, Conversation>)
    suspend fun withPersistMembers(result: Either<StorageFailure, Unit>)
    suspend fun withMembersNameAndHandle(result: Either<StorageFailure, Map<UserId, NameAndHandle>>)
    suspend fun withClearContentSucceeding()
    suspend fun withGetConversationMembers(result: List<UserId>)
    suspend fun withUpdateLegalHoldStatusSuccess(isChanged: Boolean = true): ConversationRepositoryArrangement
    suspend fun withObserveConversationLegalHoldStatus(status: Conversation.LegalHoldStatus): ConversationRepositoryArrangement
    suspend fun withObserveIsUserMember(userId: UserId, isMember: Boolean): ConversationRepositoryArrangement
}

@OptIn(ConversationPersistenceApi::class)
internal open class ConversationRepositoryArrangementImpl : ConversationRepositoryArrangement {

    override val conversationRepository: ConversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)

    override suspend fun withGetGroupConversationsWithMembersWithBothDomains(
        result: Either<CoreFailure, Map<ConversationId, List<UserId>>>,
        firstDomain: (String) -> Boolean,
        secondDomain: (String) -> Boolean,
    ) {
        everySuspend {
            conversationRepository.getGroupConversationsWithMembersWithBothDomains(
                matches { firstDomain(it) },
                matches { secondDomain(it) }
            )
        }.returns(result)
    }

    override suspend fun withGetOneOnOneConversationsWithFederatedMember(
        result: Either<CoreFailure, Map<ConversationId, UserId>>,
        domain: (String) -> Boolean
    ) {
        everySuspend {
            conversationRepository.getOneOnOneConversationsWithFederatedMembers(
                matches { domain(it) }
            )
        }.returns(result)
    }

    override suspend fun withSetConversationDeletedLocallySucceeding(conversationId: (ConversationId) -> Boolean) {
        everySuspend {
            conversationRepository.setConversationDeletedLocally(matches { conversationId(it) }, any())
        }.returns(Either.Right(Unit))
    }

    override suspend fun withSetConversationDeletedLocallyFailing(conversationId: (ConversationId) -> Boolean) {
        everySuspend {
            conversationRepository.setConversationDeletedLocally(matches { conversationId(it) }, any())
        }.returns(Either.Left(CoreFailure.Unknown(RuntimeException("some error"))))
    }

    override suspend fun withDeletingConversationLocallySucceeding(conversationId: (ConversationId) -> Boolean) {
        everySuspend {
            conversationRepository.deleteConversationLocally(matches { conversationId(it) })
        }.returns(Either.Right(true))
    }

    override suspend fun withDeletingConversationLocallyFailing(conversationId: (ConversationId) -> Boolean) {
        everySuspend {
            conversationRepository.deleteConversationLocally(matches { conversationId(it) })
        }.returns(Either.Left(CoreFailure.Unknown(RuntimeException("some error"))))
    }

    override suspend fun withSetInformedAboutDegradedMLSVerificationFlagResult(result: Either<StorageFailure, Unit>) {
        everySuspend {
            conversationRepository.setInformedAboutDegradedMLSVerificationFlag(any(), any())
        }.returns(result)
    }

    override suspend fun withInformedAboutDegradedMLSVerification(isInformed: Either<StorageFailure, Boolean>) = apply {
        everySuspend {
            conversationRepository.isInformedAboutDegradedMLSVerification(any())
        }.returns(isInformed)
    }

    override suspend fun withConversationProtocolInfo(result: Either<StorageFailure, Conversation.ProtocolInfo>) = apply {
        everySuspend {
            conversationRepository.getConversationProtocolInfo(any())
        }.returns(result)
    }

    override suspend fun withUpdateVerificationStatus(result: Either<StorageFailure, Unit>) = apply {
        everySuspend {
            conversationRepository.updateMlsVerificationStatus(any(), any())
        }.returns(result)
    }

    override suspend fun withConversationByMLSGroupId(result: Either<StorageFailure, Conversation>) = apply {
        everySuspend {
            conversationRepository.getConversationByMLSGroupId(any())
        }.returns(result)
    }

    override suspend fun withUpdateProtocolLocally(result: Either<CoreFailure, ConversationProtocolUpdateStatus>) {
        everySuspend {
            conversationRepository.updateProtocolLocally(any(), any())
        }.returns(result)
    }

    override suspend fun withConversationsForUserIdReturning(result: Either<CoreFailure, List<Conversation>>) {
        everySuspend {
            conversationRepository.getConversationsByUserId(any())
        }.returns(result)
    }

    override suspend fun withFetchMlsOneToOneConversation(result: Either<CoreFailure, ConversationResponse>) {
        everySuspend {
            conversationRepository.fetchMlsOneToOneConversation(any())
        }.returns(result)
    }

    override suspend fun withObserveOneToOneConversationWithOtherUserReturning(result: Either<CoreFailure, Conversation>) {
        everySuspend {
            conversationRepository.observeOneToOneConversationWithOtherUser(any())
        }.returns(flowOf(result))
    }

    override suspend fun withObserveConversationDetailsByIdReturning(vararg results: Either<StorageFailure, ConversationDetails>) {
        everySuspend {
            conversationRepository.observeConversationDetailsById(any())
        }.returns(flowOf(*results))
    }

    override suspend fun withGetConversationIdsReturning(result: Either<StorageFailure, List<QualifiedID>>) {
        everySuspend {
            conversationRepository.getConversationIds(any(), any())
        }.returns(result)
    }

    override suspend fun withGetOneOnOneConversationsWithOtherUserReturning(result: Either<StorageFailure, List<QualifiedID>>) {
        everySuspend {
            conversationRepository.getOneOnOneConversationsWithOtherUser(any(), any())
        }.returns(result)
    }

    override suspend fun withGetConversationProtocolInfo(result: Either<StorageFailure, Conversation.ProtocolInfo>) {
        everySuspend {
            conversationRepository.getConversationProtocolInfo(any())
        }.returns(result)
    }

    override suspend fun withGetConversationByIdReturning(conversation: Conversation?) {
        everySuspend {
            conversationRepository.getConversationById(any())
        }.returns(
            if (conversation != null) Either.Right(conversation)
            else Either.Left(StorageFailure.DataNotFound)
        )
    }

    override suspend fun withSetDegradedConversationNotifiedFlag(result: Either<CoreFailure, Unit>) {
        everySuspend {
            conversationRepository.setDegradedConversationNotifiedFlag(any(), any())
        }.returns(result)
    }

    override suspend fun withSelectGroupStatusMembersNamesAndHandles(result: Either<StorageFailure, EpochChangesData>) {
        everySuspend {
            conversationRepository.getGroupStatusMembersNamesAndHandles(any())
        }.returns(result)
    }

    override suspend fun withConversationDetailsByIdReturning(result: Either<StorageFailure, Conversation>) {
        everySuspend {
            conversationRepository.getConversationById(any())
        }.returns(result)
    }

    override suspend fun withPersistMembers(result: Either<StorageFailure, Unit>) {
        everySuspend { conversationRepository.persistMembers(any(), any()) }.returns(result)
    }

    override suspend fun withMembersNameAndHandle(result: Either<StorageFailure, Map<UserId, NameAndHandle>>) {
        everySuspend { conversationRepository.selectMembersNameAndHandle(any()) }.returns(result)
    }

    override suspend fun withClearContentSucceeding() {
        everySuspend { conversationRepository.clearContent(any()) }.returns(Either.Right(Unit))
    }

    override suspend fun withGetConversationMembers(result: List<UserId>) {
        everySuspend { conversationRepository.getConversationMembers(any()) }.returns(Either.Right(result))
    }

    override suspend fun withUpdateLegalHoldStatusSuccess(isChanged: Boolean) = apply {
        everySuspend { conversationRepository.updateLegalHoldStatus(any(), any()) }.returns(Either.Right(isChanged))
    }

    override suspend fun withObserveConversationLegalHoldStatus(status: Conversation.LegalHoldStatus) = apply {
        everySuspend { conversationRepository.observeLegalHoldStatus(any()) }.returns(flowOf(Either.Right(status)))
    }

    override suspend fun withObserveIsUserMember(userId: UserId, isMember: Boolean) = apply {
        everySuspend { conversationRepository.observeIsUserMember(any(), eq(userId)) }.returns(flowOf(Either.Right(isMember)))
    }
}
