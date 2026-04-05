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
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.MemberLeaveReason
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.call.usecase.UpdateConversationClientsForCurrentCallUseCase
import com.wire.kalium.logic.feature.conversation.delete.DeleteConversationUseCase
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.member.MemberDAO
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.matching
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import dev.mokkery.matcher.any as mokkeryAny

internal class MemberLeaveEventHandlerTest {

    @Test
    fun givenDaoReturnsSuccess_whenDeletingMember_thenPersistSystemMessage() = runTest {

        val event = memberLeaveEvent(reason = MemberLeaveReason.Left)
        val message = memberRemovedMessage(event)

        val (arrangement, memberLeaveEventHandler) = Arrangement()
            .arrange {
                withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit), userIdList = event.removedList.toSet())
                withPersistingMessage(Either.Right(Unit), message = message)
                withDeleteMembersByQualifiedID(
                    result = event.removedList.size.toLong(),
                    conversationId = event.conversationId.toDao(),
                    memberIdList = event.removedList.map { it.toDao() }
                )
                withGetConversationProtocolInfoReturns(ConversationEntity.ProtocolInfo.Proteus)
                withGetConversationsDeleteQueue(emptyList())
            }

        memberLeaveEventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.memberDAO.deleteMembersByQualifiedID(event.removedList.map { it.toDao() }, qualifiedConversationIdEntity)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.updateConversationClientsForCurrentCall.invoke(message.conversationId)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessageUseCase.invoke(message)
        }

    }

    @Test
    fun givenDaoReturnsFailure_whenDeletingMember_thenNothingToDo() = runTest {
        val event = memberLeaveEvent(reason = MemberLeaveReason.Left)

        val (arrangement, memberLeaveEventHandler) = Arrangement()
            .arrange {
                withFetchUsersIfUnknownByIdsReturning(
                    Either.Left(failure),
                    userIdList = event.removedList.toSet()
                )
                withPersistingMessage(Either.Left(failure))
                withDeleteMembersByQualifiedIDThrows(throws = IllegalArgumentException())
                withGetConversationProtocolInfoReturns(ConversationEntity.ProtocolInfo.Proteus)
            }

        memberLeaveEventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.memberDAO.deleteMembersByQualifiedID(event.removedList.map { it.toDao() }, qualifiedConversationIdEntity)
        }

        verifySuspend(VerifyMode.not) {
            arrangement.persistMessageUseCase.invoke(memberRemovedMessage(event))
        }
    }

    @Test
    fun givenDaoReturnsSuccess_whenDeletingMember_thenPersistSystemMessageAndFetchUsers() = runTest {
        val event = memberLeaveEvent(reason = MemberLeaveReason.UserDeleted)
        val message = memberRemovedFromTeamMessage(event)

        val (arrangement, memberLeaveEventHandler) = Arrangement()
            .arrange {
                withMarkAsDeleted(Either.Right(Unit), userId = event.removedList)
                withDeleteMembersByQualifiedID(
                    result = event.removedList.size.toLong(),
                    conversationId = event.conversationId.toDao(),
                    memberIdList = event.removedList.map { it.toDao() }
                )
                withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit), userIdList = event.removedList.toSet())
                withPersistingMessage(Either.Right(Unit), message = message)
                withTeamId(Either.Right(TeamId("teamId")))
                withIsAtLeastOneUserATeamMember(Either.Right(true))
                withGetConversationProtocolInfoReturns(ConversationEntity.ProtocolInfo.Proteus)
            }

        memberLeaveEventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userRepository.fetchUsersIfUnknownByIds(event.removedList.toSet())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.updateConversationClientsForCurrentCall.invoke(message.conversationId)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessageUseCase.invoke(message)
        }
    }

    @Test
    fun givenDaoReturnsSuccess_whenDeletingMemberAndSelfIsNotTeamMember_thenDoNothing() = runTest {
        val event = memberLeaveEvent(reason = MemberLeaveReason.UserDeleted)
        val (arrangement, memberLeaveEventHandler) = Arrangement()
            .arrange {
                withMarkAsDeleted(Either.Right(Unit), userId = event.removedList)
                withDeleteMembersByQualifiedID(
                    result = event.removedList.size.toLong(),
                    conversationId = event.conversationId.toDao(),
                    memberIdList = event.removedList.map { it.toDao() }
                )
                withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit), userIdList = event.removedList.toSet())
                withTeamId(Either.Right(null))
                withPersistingMessage(Either.Right(Unit))
                withGetConversationProtocolInfoReturns(ConversationEntity.ProtocolInfo.Proteus)
            }

        memberLeaveEventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userRepository.fetchUsersIfUnknownByIds(event.removedList.toSet())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userRepository.markAsDeleted(mokkeryAny())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.memberDAO.deleteMembersByQualifiedID(mokkeryAny(), mokkeryAny())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.updateConversationClientsForCurrentCall.invoke(event.conversationId)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessageUseCase.invoke(
                matching {
                    it.content is MessageContent.MemberChange.Removed
                }
            )
        }
    }

    @Test
    fun givenNotMembersRemoved_whenResolvingMessageContent_thenNotMessagePersisted() = runTest {
        val event = memberLeaveEvent(reason = MemberLeaveReason.UserDeleted)

        val (arrangement, memberLeaveEventHandler) = Arrangement()
            .arrange {
                withTeamId(Either.Right(TeamId("teamId")))
                withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
                withMarkAsDeleted(Either.Right(Unit), userId = event.removedList)
                withDeleteMembersByQualifiedID(
                    result = 0,
                    conversationId = event.conversationId.toDao(),
                    memberIdList = event.removedList.map { it.toDao() }
                )
                withIsAtLeastOneUserATeamMember(Either.Right(false))
                withGetConversationProtocolInfoReturns(ConversationEntity.ProtocolInfo.Proteus)
            }

        memberLeaveEventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userRepository.fetchUsersIfUnknownByIds(event.removedList.toSet())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userRepository.markAsDeleted(mokkeryAny())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.memberDAO.deleteMembersByQualifiedID(mokkeryAny(), mokkeryAny())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.updateConversationClientsForCurrentCall.invoke(event.conversationId)
        }

        verifySuspend(VerifyMode.not) {
            arrangement.persistMessageUseCase.invoke(mokkeryAny())
        }
    }

    @Test
    fun givenMemberLeaveEvent_whenHandlingIt_thenShouldUpdateConversationLegalHoldIfNeeded() = runTest {
        // given
        val event = memberLeaveEvent(reason = MemberLeaveReason.Left)
        val (arrangement, memberLeaveEventHandler) = Arrangement()
            .arrange {
                withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit), userIdList = event.removedList.toSet())
                withPersistingMessage(Either.Right(Unit))
                withDeleteMembersByQualifiedID(
                    result = event.removedList.size.toLong(),
                    conversationId = event.conversationId.toDao(),
                    memberIdList = event.removedList.map { it.toDao() }
                )
                withGetConversationProtocolInfoReturns(ConversationEntity.ProtocolInfo.Proteus)
                withGetConversationsDeleteQueue(emptyList())
            }
        // when
        memberLeaveEventHandler.handle(arrangement.transactionContext, event)
        // then
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.legalHoldHandler.handleConversationMembersChanged(event.conversationId)
        }
    }

    @Test
    fun givenDaoReturnsSuccessAndConversationInDeleteQueue_whenDeletingSelfMember_thenConversationDeleted() = runTest {
        val event = memberLeaveEvent(reason = MemberLeaveReason.Left).copy(removedList = listOf(selfUserId), removedBy = selfUserId)

        val (arrangement, memberLeaveEventHandler) = Arrangement()
            .arrange {
                withDeleteMembersByQualifiedID(
                    result = event.removedList.size.toLong(),
                    conversationId = event.conversationId.toDao(),
                    memberIdList = event.removedList.map { QualifiedIDEntity(it.value, it.domain) }
                )
                withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit), userIdList = event.removedList.toSet())
                withTeamId(Either.Right(null))
                withPersistingMessage(Either.Right(Unit))
                withGetConversationsDeleteQueue(listOf(event.conversationId))
                withDeletingConversationSucceeding(event.conversationId)
                withGetConversationProtocolInfoReturns(ConversationEntity.ProtocolInfo.Proteus)
            }

        memberLeaveEventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.updateConversationClientsForCurrentCall.invoke(event.conversationId)
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.getConversationsDeleteQueue()
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.deleteConversation(mokkeryAny(), event.conversationId)
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.removeConversationFromDeleteQueue(event.conversationId)
        }
    }

    @Test
    fun givenOtherUsersRemainInMLSGroup_whenHandlingMemberLeaveEvent_thenDoNotWipeConversation() = runTest {
        val event = memberLeaveEvent(reason = MemberLeaveReason.Removed).copy(
            removedList = listOf(UserId("userId1", "domain"), UserId("userId2", "domain"))
        )

        val (arrangement, memberLeaveEventHandler) = Arrangement()
            .arrange {
                withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit), userIdList = event.removedList.toSet())
                withPersistingMessage(Either.Right(Unit))
                withTeamId(Either.Right(null))
                withDeleteMembersByQualifiedID(
                    result = event.removedList.size.toLong(),
                    conversationId = event.conversationId.toDao(),
                    memberIdList = event.removedList.map { it.toDao() }
                )
                withGetConversationsDeleteQueue(listOf(event.conversationId))
                withDeletingConversationSucceeding(event.conversationId)
                withGetConversationProtocolInfoReturns(mlsProtocolInfo1)
            }

        memberLeaveEventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.mlsContext.wipeConversation(mokkeryAny())
        }
    }

    @Test
    fun givenEventWithConversationMissingFormDB_whenConversationIsMissingFromDB_thenIgnoreAndReturnSuccess() = runTest {
        val event = memberLeaveEvent(reason = MemberLeaveReason.Removed).copy(
            removedList = listOf(selfUserId)
        )

        val (arrangement, memberLeaveEventHandler) = Arrangement()
            .arrange {
                withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit), userIdList = event.removedList.toSet())
                withDeleteMembersByQualifiedID(
                    result = event.removedList.size.toLong(),
                    conversationId = event.conversationId.toDao(),
                    memberIdList = event.removedList.map { it.toDao() }
                )
                withPersistingMessage(Either.Right(Unit))
                withGetConversationProtocolInfoReturns(null)
            }

        memberLeaveEventHandler.handle(arrangement.transactionContext, event).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.memberDAO.deleteMembersByQualifiedID(mokkeryAny(), mokkeryAny())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.updateConversationClientsForCurrentCall.invoke(mokkeryAny())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessageUseCase.invoke(mokkeryAny())
        }
    }

    private class Arrangement :
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val userRepository = mock<UserRepository>()
        val persistMessageUseCase = mock<PersistMessageUseCase>()
        val memberDAO = mock<MemberDAO>()
        val selfTeamIdProvider = mock<SelfTeamIdProvider>()
        val deleteConversation = mock<DeleteConversationUseCase>()
        val conversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)
        val updateConversationClientsForCurrentCall = mock<UpdateConversationClientsForCurrentCallUseCase>(mode = MockMode.autoUnit)
        val legalHoldHandler = mock<LegalHoldHandler>()

        private lateinit var memberLeaveEventHandler: MemberLeaveEventHandler

        fun withFetchUsersIfUnknownByIdsReturning(
            result: Either<CoreFailure, Unit>,
            userIdList: Set<UserId>? = null
        ) = apply {
            if (userIdList == null) {
                everySuspend { userRepository.fetchUsersIfUnknownByIds(mokkeryAny()) } returns result
            } else {
                everySuspend { userRepository.fetchUsersIfUnknownByIds(userIdList) } returns result
            }
        }

        fun withPersistingMessage(
            result: Either<CoreFailure, Unit>,
            message: Message.Standalone? = null
        ) = apply {
            if (message == null) {
                everySuspend { persistMessageUseCase.invoke(mokkeryAny()) } returns result
            } else {
                everySuspend { persistMessageUseCase.invoke(message) } returns result
            }
        }

        fun withDeleteMembersByQualifiedID(
            result: Long,
            conversationId: QualifiedIDEntity? = null,
            memberIdList: List<QualifiedIDEntity>? = null
        ) = apply {
            if (conversationId == null || memberIdList == null) {
                everySuspend { memberDAO.deleteMembersByQualifiedID(mokkeryAny(), mokkeryAny()) } returns result
            } else {
                everySuspend { memberDAO.deleteMembersByQualifiedID(memberIdList, conversationId) } returns result
            }
        }

        fun withDeleteMembersByQualifiedIDThrows(
            throws: Throwable,
            conversationId: QualifiedIDEntity? = null,
            memberIdList: List<QualifiedIDEntity>? = null
        ) = apply {
            if (conversationId == null || memberIdList == null) {
                everySuspend { memberDAO.deleteMembersByQualifiedID(mokkeryAny(), mokkeryAny()) } throws throws
            } else {
                everySuspend { memberDAO.deleteMembersByQualifiedID(memberIdList, conversationId) } throws throws
            }
        }

        fun withMarkAsDeleted(result: Either<StorageFailure, Unit>, userId: List<UserId>? = null) = apply {
            if (userId == null) {
                everySuspend { userRepository.markAsDeleted(mokkeryAny()) } returns result
            } else {
                everySuspend { userRepository.markAsDeleted(userId) } returns result
            }
        }

        fun withTeamId(teamId: Either<StorageFailure, TeamId?>) = apply {
            everySuspend { selfTeamIdProvider.invoke() } returns teamId
        }

        fun withIsAtLeastOneUserATeamMember(
            result: Either<StorageFailure, Boolean>,
            userIdList: List<UserId>? = null
        ) = apply {
            if (userIdList == null) {
                everySuspend { userRepository.isAtLeastOneUserATeamMember(mokkeryAny(), mokkeryAny()) } returns result
            } else {
                everySuspend { userRepository.isAtLeastOneUserATeamMember(userIdList, mokkeryAny()) } returns result
            }
        }

        fun withGetConversationProtocolInfoReturns(protocolInfo: ConversationEntity.ProtocolInfo?) = apply {
            // MemberLeaveEventHandler no longer depends on protocol info directly; keep this hook to preserve test setup readability.
        }

        fun withGetConversationsDeleteQueue(result: List<ConversationId>) = apply {
            everySuspend { conversationRepository.getConversationsDeleteQueue() } returns result
        }

        fun withDeletingConversationSucceeding(conversationId: ConversationId? = null) = apply {
            if (conversationId == null) {
                everySuspend { deleteConversation(mokkeryAny(), mokkeryAny()) } returns Either.Right(Unit)
            } else {
                everySuspend { deleteConversation(mokkeryAny(), conversationId) } returns Either.Right(Unit)
            }
        }

        suspend fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, MemberLeaveEventHandler> = run {
            everySuspend {
                legalHoldHandler.handleConversationMembersChanged(mokkeryAny())
            } returns Either.Right(Unit)
            block()
            memberLeaveEventHandler = MemberLeaveEventHandlerImpl(
                memberDAO = memberDAO,
                userRepository = userRepository,
                conversationRepository = conversationRepository,
                persistMessage = persistMessageUseCase,
                updateConversationClientsForCurrentCall = lazy { updateConversationClientsForCurrentCall },
                legalHoldHandler = legalHoldHandler,
                selfTeamIdProvider = selfTeamIdProvider,
                selfUserId = selfUserId,
                deleteConversation = deleteConversation
            )
            this to memberLeaveEventHandler
        }
    }

    companion object {
        val failure = CoreFailure.MissingClientRegistration
        val selfUserId = UserId("self-userId", "domain")
        val userId = UserId("userId", "domain")
        private val qualifiedConversationIdEntity = QualifiedIDEntity("conversationId", "domain")

        val conversationId = ConversationId("conversationId", "domain")

        val mlsProtocolInfo1 = ConversationEntity.ProtocolInfo.MLS(
            "group2",
            ConversationEntity.GroupState.ESTABLISHED,
            0UL,
            Instant.parse("2021-03-30T15:36:00.000Z"),
            cipherSuite = ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
        )

        fun memberLeaveEvent(reason: MemberLeaveReason) = Event.Conversation.MemberLeave(
            id = "id",
            conversationId = conversationId,
            removedBy = selfUserId,
            removedList = listOf(selfUserId),
            dateTime = Instant.UNIX_FIRST_DATE,
            reason = reason
        )

        fun memberRemovedMessage(event: Event.Conversation.MemberLeave) = Message.System(
            id = event.id,
            content = MessageContent.MemberChange.Removed(members = event.removedList),
            conversationId = event.conversationId,
            date = event.dateTime,
            senderUserId = event.removedBy,
            status = Message.Status.Sent,
            visibility = Message.Visibility.VISIBLE,
            expirationData = null
        )

        fun memberRemovedFromTeamMessage(event: Event.Conversation.MemberLeave) = Message.System(
            id = event.id,
            content = MessageContent.MemberChange.RemovedFromTeam(members = event.removedList),
            conversationId = event.conversationId,
            date = event.dateTime,
            senderUserId = event.removedBy,
            status = Message.Status.Sent,
            visibility = Message.Visibility.VISIBLE,
            expirationData = null
        )
    }
}
