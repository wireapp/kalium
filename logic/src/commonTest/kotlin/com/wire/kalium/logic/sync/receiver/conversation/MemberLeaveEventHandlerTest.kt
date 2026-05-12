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
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.MemberLeaveReason
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.call.usecase.UpdateConversationClientsForCurrentCallUseCase
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.persistence.dao.member.MemberDAO
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matches
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test

internal class MemberLeaveEventHandlerTest {

    @Test
    fun givenDaoReturnsSuccess_whenDeletingMember_thenPersistSystemMessage() = runTest {

        val event = memberLeaveEvent(reason = MemberLeaveReason.Left)
        val message = memberRemovedMessage(event)

        val (arrangement, memberLeaveEventHandler) = Arrangement()
            .arrange {
                withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
                withPersistingMessage(Either.Right(Unit))
                withDeleteMembersByQualifiedID(
                    result = event.removedList.size.toLong(),
                )
                withGetConversationProtocolInfo(Either.Right(Conversation.ProtocolInfo.Proteus))
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
                    Either.Left(failure)
                )
                withPersistingMessage(Either.Left(failure))
                withDeleteMembersByQualifiedIDThrows(throws = IllegalArgumentException())
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
                withMarkAsDeleted(Either.Right(Unit))
                withDeleteMembersByQualifiedID(
                    result = event.removedList.size.toLong(),
                )
                withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
                withPersistingMessage(Either.Right(Unit))
                withTeamId(Either.Right(TeamId("teamId")))
                withIsAtLeastOneUserATeamMember(Either.Right(true))
                withGetConversationProtocolInfo(Either.Right(Conversation.ProtocolInfo.Proteus))
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
                withMarkAsDeleted(Either.Right(Unit))
                withDeleteMembersByQualifiedID(
                    result = event.removedList.size.toLong(),
                )
                withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
                withTeamId(Either.Right(null))
                withPersistingMessage(Either.Right(Unit))
                withGetConversationProtocolInfo(Either.Right(Conversation.ProtocolInfo.Proteus))
            }

        memberLeaveEventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userRepository.fetchUsersIfUnknownByIds(event.removedList.toSet())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userRepository.markAsDeleted(any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.memberDAO.deleteMembersByQualifiedID(any(), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.updateConversationClientsForCurrentCall.invoke(eq(event.conversationId))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessageUseCase.invoke(
                matches {
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
                withMarkAsDeleted(Either.Right(Unit))
                withDeleteMembersByQualifiedID(
                    result = 0,
                )
                withIsAtLeastOneUserATeamMember(Either.Right(false))
                withGetConversationProtocolInfo(Either.Right(Conversation.ProtocolInfo.Proteus))
            }

        memberLeaveEventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userRepository.fetchUsersIfUnknownByIds(event.removedList.toSet())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userRepository.markAsDeleted(any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.memberDAO.deleteMembersByQualifiedID(any(), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.updateConversationClientsForCurrentCall.invoke(eq(event.conversationId))
        }

        verifySuspend(VerifyMode.not) {
            arrangement.persistMessageUseCase.invoke(any())
        }
    }

    @Test
    fun givenMemberLeaveEvent_whenHandlingIt_thenShouldUpdateConversationLegalHoldIfNeeded() = runTest {
        // given
        val event = memberLeaveEvent(reason = MemberLeaveReason.Left)
        val (arrangement, memberLeaveEventHandler) = Arrangement()
            .arrange {
                withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
                withPersistingMessage(Either.Right(Unit))
                withDeleteMembersByQualifiedID(
                    result = event.removedList.size.toLong(),
                )
                withGetConversationProtocolInfo(Either.Right(Conversation.ProtocolInfo.Proteus))
            }
        // when
        memberLeaveEventHandler.handle(arrangement.transactionContext, event)
        // then
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.legalHoldHandler.handleConversationMembersChanged(eq(event.conversationId))
        }
    }

    @Test
    fun givenSelfUserLeftMLSConversation_whenHandlingMemberLeave_thenLeaveGroupCalled() = runTest {
        val event = memberLeaveEvent(reason = MemberLeaveReason.Left).copy(removedList = listOf(selfUserId), removedBy = selfUserId)

        val (arrangement, memberLeaveEventHandler) = Arrangement()
            .arrange {
                withDeleteMembersByQualifiedID(
                    result = event.removedList.size.toLong(),
                )
                withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
                withTeamId(Either.Right(null))
                withPersistingMessage(Either.Right(Unit))
                withGetConversationProtocolInfo(Either.Right(MLS_DOMAIN_PROTOCOL_INFO))
                withSuccessfulLeaveGroup(MLS_GROUP_ID)
            }

        memberLeaveEventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.leaveGroup(any(), eq(MLS_GROUP_ID))
        }
    }

    @Test
    fun givenSelfUserRemovedFromMLSConversation_whenHandlingMemberLeave_thenLeaveGroupCalled() = runTest {
        val event = memberLeaveEvent(reason = MemberLeaveReason.Removed).copy(removedList = listOf(selfUserId))

        val (arrangement, memberLeaveEventHandler) = Arrangement()
            .arrange {
                withDeleteMembersByQualifiedID(
                    result = event.removedList.size.toLong(),
                )
                withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
                withTeamId(Either.Right(null))
                withPersistingMessage(Either.Right(Unit))
                withGetConversationProtocolInfo(Either.Right(MLS_DOMAIN_PROTOCOL_INFO))
                withSuccessfulLeaveGroup(MLS_GROUP_ID)
            }

        memberLeaveEventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.leaveGroup(any(), eq(MLS_GROUP_ID))
        }
    }

    @Test
    fun givenSelfUserRemovedWithOtherUsersFromMLSConversation_whenHandlingMemberLeave_thenLeaveGroupCalled() = runTest {
        val event = memberLeaveEvent(reason = MemberLeaveReason.Removed).copy(
            removedList = listOf(selfUserId, userId)
        )

        val (arrangement, memberLeaveEventHandler) = Arrangement()
            .arrange {
                withDeleteMembersByQualifiedID(
                    result = event.removedList.size.toLong(),
                )
                withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
                withTeamId(Either.Right(null))
                withPersistingMessage(Either.Right(Unit))
                withGetConversationProtocolInfo(Either.Right(MLS_DOMAIN_PROTOCOL_INFO))
                withSuccessfulLeaveGroup(MLS_GROUP_ID)
            }

        memberLeaveEventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.leaveGroup(any(), eq(MLS_GROUP_ID))
        }
    }

    @Test
    fun givenOtherUsersRemovedFromConversation_whenHandlingMemberLeave_thenLeaveGroupNotCalled() = runTest {
        val event = memberLeaveEvent(reason = MemberLeaveReason.Removed).copy(
            removedList = listOf(UserId("userId1", "domain"), UserId("userId2", "domain"))
        )

        val (arrangement, memberLeaveEventHandler) = Arrangement()
            .arrange {
                withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
                withPersistingMessage(Either.Right(Unit))
                withTeamId(Either.Right(null))
                withDeleteMembersByQualifiedID(
                    result = event.removedList.size.toLong(),
                )
            }

        memberLeaveEventHandler.handle(arrangement.transactionContext, event)

        verifySuspend(VerifyMode.not) { arrangement.mlsConversationRepository.leaveGroup(any(), any()) }
    }

    @Test
    fun givenEventWithConversationMissingFormDB_whenConversationIsMissingFromDB_thenIgnoreAndReturnSuccess() = runTest {
        val event = memberLeaveEvent(reason = MemberLeaveReason.Removed).copy(
            removedList = listOf(selfUserId)
        )

        val (arrangement, memberLeaveEventHandler) = Arrangement()
            .arrange {
                withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
                withDeleteMembersByQualifiedID(
                    result = event.removedList.size.toLong(),
                )
                withGetConversationProtocolInfo(Either.Left(StorageFailure.DataNotFound))
                withPersistingMessage(Either.Right(Unit))
            }

        memberLeaveEventHandler.handle(arrangement.transactionContext, event).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.memberDAO.deleteMembersByQualifiedID(any(), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.updateConversationClientsForCurrentCall.invoke(any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessageUseCase.invoke(any())
        }
    }

    private class Arrangement {
        val memberDAO = mock<MemberDAO>(mode = MockMode.autoUnit)
        val userRepository = mock<UserRepository>(mode = MockMode.autoUnit)
        val conversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)
        val persistMessageUseCase = mock<PersistMessageUseCase>(mode = MockMode.autoUnit)
        val selfTeamIdProvider = mock<SelfTeamIdProvider>(mode = MockMode.autoUnit)
        val mlsConversationRepository = mock<MLSConversationRepository>(mode = MockMode.autoUnit)
        val transactionContext = mock<CryptoTransactionContext>(mode = MockMode.autoUnit)
        private val mlsContext = mock<MlsCoreCryptoContext>(mode = MockMode.autoUnit)
        val updateConversationClientsForCurrentCall = mock<UpdateConversationClientsForCurrentCallUseCase>(mode = MockMode.autoUnit)
        val legalHoldHandler = mock<LegalHoldHandler>(mode = MockMode.autoUnit)

        private lateinit var memberLeaveEventHandler: MemberLeaveEventHandler

        suspend fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, MemberLeaveEventHandler> = run {
            everySuspend {
                legalHoldHandler.handleConversationMembersChanged(any())
            } returns (Either.Right(Unit))
            everySuspend {
                updateConversationClientsForCurrentCall.invoke(any())
            } returns (Unit)
            every { transactionContext.mls } returns (mlsContext)
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
                mlsConversationRepository = mlsConversationRepository
            )
            this to memberLeaveEventHandler
        }

        suspend fun withFetchUsersIfUnknownByIdsReturning(result: Either<CoreFailure, Unit>) {
            everySuspend { userRepository.fetchUsersIfUnknownByIds(any()) } returns (result)
        }

        suspend fun withPersistingMessage(result: Either<CoreFailure, Unit>) {
            everySuspend { persistMessageUseCase.invoke(any()) } returns (result)
        }

        suspend fun withDeleteMembersByQualifiedID(result: Long) {
            everySuspend { memberDAO.deleteMembersByQualifiedID(any(), any()) } returns (result)
        }

        suspend fun withDeleteMembersByQualifiedIDThrows(throws: Throwable) {
            everySuspend { memberDAO.deleteMembersByQualifiedID(any(), any()) } throws throws
        }

        suspend fun withMarkAsDeleted(result: Either<StorageFailure, Unit>) {
            everySuspend { userRepository.markAsDeleted(any()) } returns (result)
        }

        suspend fun withTeamId(result: Either<CoreFailure, TeamId?>) {
            everySuspend { selfTeamIdProvider.invoke() } returns (result)
        }

        suspend fun withIsAtLeastOneUserATeamMember(result: Either<StorageFailure, Boolean>) {
            everySuspend { userRepository.isAtLeastOneUserATeamMember(any(), any()) } returns (result)
        }

        suspend fun withGetConversationProtocolInfo(result: Either<StorageFailure, Conversation.ProtocolInfo>) {
            everySuspend { conversationRepository.getConversationProtocolInfo(any()) } returns (result)
        }

        suspend fun withSuccessfulLeaveGroup(groupId: GroupID) {
            everySuspend { mlsConversationRepository.leaveGroup(any(), eq(groupId)) } returns (Either.Right(Unit))
        }
    }

    companion object {
        val failure = CoreFailure.MissingClientRegistration
        val selfUserId = UserId("self-userId", "domain")
        val userId = UserId("userId", "domain")
        private val qualifiedConversationIdEntity = QualifiedIDEntity("conversationId", "domain")

        val conversationId = ConversationId("conversationId", "domain")

        val MLS_GROUP_ID = GroupID("group2")
        val MLS_DOMAIN_PROTOCOL_INFO = Conversation.ProtocolInfo.MLS(
            groupId = MLS_GROUP_ID,
            groupState = Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED,
            epoch = 0UL,
            keyingMaterialLastUpdate = Instant.parse("2021-03-30T15:36:00.000Z"),
            cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
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
