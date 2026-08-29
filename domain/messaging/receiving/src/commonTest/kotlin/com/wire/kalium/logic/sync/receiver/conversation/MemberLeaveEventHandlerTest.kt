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
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.MLSResetEventRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.MemberLeaveReason
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.mls.ConversationProtocolGetter
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.sync.receiver.EventMessagePersistence
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

internal class MemberLeaveEventHandlerTest {

    @Test
    fun givenDaoReturnsSuccess_whenDeletingMember_thenPersistSystemMessage() = runTest {
        val event = memberLeaveEvent(MemberLeaveReason.Left)
        val arrangement = Arrangement()

        arrangement.handler.handle(arrangement.transactionContext, event)

        assertEquals(listOf(DeleteMembersCall(event.removedList, event.conversationId)), arrangement.deleteMembersCalls)
        assertEquals(listOf(event.conversationId), arrangement.updateCurrentCallCalls)
        assertEquals(memberRemovedMessage(event), arrangement.persistedMessages.single())
    }

    @Test
    fun givenDaoReturnsFailure_whenDeletingMember_thenNothingToDo() = runTest {
        val event = memberLeaveEvent(MemberLeaveReason.Left)
        val arrangement = Arrangement().apply { deleteMembersResult = Either.Left(StorageFailure.DataNotFound) }

        val result = arrangement.handler.handle(arrangement.transactionContext, event)

        assertEquals(Either.Left(StorageFailure.DataNotFound), result)
        assertEquals(listOf(DeleteMembersCall(event.removedList, event.conversationId)), arrangement.deleteMembersCalls)
        assertTrue(arrangement.persistedMessages.isEmpty())
        assertTrue(arrangement.updateCurrentCallCalls.isEmpty())
    }

    @Test
    fun givenDaoReturnsSuccess_whenDeletingMember_thenPersistSystemMessageAndFetchUsers() = runTest {
        val event = memberLeaveEvent(MemberLeaveReason.UserDeleted)
        val arrangement = Arrangement().apply {
            selfTeamResult = Either.Right(TEAM_ID)
            isTeamMemberResult = Either.Right(true)
        }

        arrangement.handler.handle(arrangement.transactionContext, event)

        assertEquals(listOf(event.removedList.toSet()), arrangement.fetchUsersCalls)
        assertEquals(listOf(event.conversationId), arrangement.updateCurrentCallCalls)
        assertEquals(memberRemovedFromTeamMessage(event), arrangement.persistedMessages.single())
    }

    @Test
    fun givenDaoReturnsSuccess_whenDeletingMemberAndSelfIsNotTeamMember_thenDoNothing() = runTest {
        val event = memberLeaveEvent(MemberLeaveReason.UserDeleted)
        val arrangement = Arrangement().apply { selfTeamResult = Either.Right(null) }

        arrangement.handler.handle(arrangement.transactionContext, event)

        assertEquals(listOf(event.removedList.toSet()), arrangement.fetchUsersCalls)
        assertEquals(listOf(event.removedList), arrangement.markDeletedCalls)
        assertEquals(listOf(DeleteMembersCall(event.removedList, event.conversationId)), arrangement.deleteMembersCalls)
        assertEquals(listOf(event.conversationId), arrangement.updateCurrentCallCalls)
        assertTrue(arrangement.persistedMessages.single().content is MessageContent.MemberChange.Removed)
    }

    @Test
    fun givenNotMembersRemoved_whenResolvingMessageContent_thenNotMessagePersisted() = runTest {
        val event = memberLeaveEvent(MemberLeaveReason.UserDeleted)
        val arrangement = Arrangement().apply { deleteMembersResult = Either.Right(0) }

        val result = arrangement.handler.handle(arrangement.transactionContext, event)

        assertEquals(Either.Right(Unit), result)
        assertEquals(listOf(event.removedList.toSet()), arrangement.fetchUsersCalls)
        assertEquals(listOf(event.removedList), arrangement.markDeletedCalls)
        assertEquals(listOf(DeleteMembersCall(event.removedList, event.conversationId)), arrangement.deleteMembersCalls)
        assertEquals(listOf(event.conversationId), arrangement.updateCurrentCallCalls)
        assertTrue(arrangement.persistedMessages.isEmpty())
        assertTrue(arrangement.selfTeamCalls == 0)
        assertTrue(arrangement.legalHoldCalls.isEmpty())
    }

    @Test
    fun givenMemberLeaveEvent_whenHandlingIt_thenShouldUpdateConversationLegalHoldIfNeeded() = runTest {
        val event = memberLeaveEvent(MemberLeaveReason.Left)
        val arrangement = Arrangement()

        arrangement.handler.handle(arrangement.transactionContext, event)

        assertEquals(listOf(event.conversationId), arrangement.legalHoldCalls)
    }

    @Test
    fun givenSelfUserRemovedFromConversation_thenDeleteMeetings() = runTest {
        val event = memberLeaveEvent(MemberLeaveReason.Removed).copy(removedList = listOf(SELF_USER_ID))
        val arrangement = Arrangement()

        arrangement.handler.handle(arrangement.transactionContext, event)

        assertEquals(listOf(event.conversationId), arrangement.deleteMeetingsCalls)
    }

    @Test
    fun givenOtherUserRemovedFromConversation_thenDoNotDeleteMeetings() = runTest {
        val event = memberLeaveEvent(MemberLeaveReason.Removed).copy(removedList = listOf(USER_ID))
        val arrangement = Arrangement()

        arrangement.handler.handle(arrangement.transactionContext, event)

        assertTrue(arrangement.deleteMeetingsCalls.isEmpty())
    }

    @Test
    fun givenSelfUserLeftMLSConversation_whenHandlingMemberLeave_thenLeaveGroupCalled() = runTest {
        val event = memberLeaveEvent(MemberLeaveReason.Left).copy(removedList = listOf(SELF_USER_ID), removedBy = SELF_USER_ID)
        val arrangement = Arrangement().apply { protocolResult = Either.Right(MLS_PROTOCOL_INFO) }

        arrangement.handler.handle(arrangement.transactionContext, event)

        val call = arrangement.leaveGroupCalls.single()
        assertSame(arrangement.mlsContext, call.mlsContext)
        assertEquals(MLS_GROUP_ID, call.groupId)
    }

    @Test
    fun givenSelfUserRemovedFromMLSConversation_whenHandlingMemberLeave_thenLeaveGroupCalled() = runTest {
        val event = memberLeaveEvent(MemberLeaveReason.Removed).copy(removedList = listOf(SELF_USER_ID))
        val arrangement = Arrangement().apply { protocolResult = Either.Right(MLS_PROTOCOL_INFO) }

        arrangement.handler.handle(arrangement.transactionContext, event)

        assertEquals(MLS_GROUP_ID, arrangement.leaveGroupCalls.single().groupId)
    }

    @Test
    fun givenSelfUserRemovedWithOtherUsersFromMLSConversation_whenHandlingMemberLeave_thenLeaveGroupCalled() = runTest {
        val event = memberLeaveEvent(MemberLeaveReason.Removed).copy(removedList = listOf(SELF_USER_ID, USER_ID))
        val arrangement = Arrangement().apply { protocolResult = Either.Right(MLS_PROTOCOL_INFO) }

        arrangement.handler.handle(arrangement.transactionContext, event)

        assertEquals(MLS_GROUP_ID, arrangement.leaveGroupCalls.single().groupId)
    }

    @Test
    fun givenOtherUsersRemovedFromConversation_whenHandlingMemberLeave_thenLeaveGroupNotCalled() = runTest {
        val event = memberLeaveEvent(MemberLeaveReason.Removed).copy(
            removedList = listOf(UserId("userId1", "domain"), UserId("userId2", "domain")),
        )
        val arrangement = Arrangement()

        arrangement.handler.handle(arrangement.transactionContext, event)

        assertTrue(arrangement.protocolCalls.isEmpty())
        assertTrue(arrangement.leaveGroupCalls.isEmpty())
    }

    @Test
    fun givenEventWithConversationMissingFormDB_whenConversationIsMissingFromDB_thenIgnoreAndReturnSuccess() = runTest {
        val event = memberLeaveEvent(MemberLeaveReason.Removed).copy(removedList = listOf(SELF_USER_ID))
        val arrangement = Arrangement().apply { protocolResult = Either.Left(StorageFailure.DataNotFound) }

        val result = arrangement.handler.handle(arrangement.transactionContext, event)

        assertEquals(Either.Right(Unit), result)
        assertEquals(listOf(DeleteMembersCall(event.removedList, event.conversationId)), arrangement.deleteMembersCalls)
        assertEquals(listOf(event.conversationId), arrangement.updateCurrentCallCalls)
        assertEquals(1, arrangement.persistedMessages.size)
    }

    @Test
    fun exactUserDeletedOrderArgumentsAndResultOwnershipArePreserved() = runTest {
        val event = memberLeaveEvent(MemberLeaveReason.UserDeleted)
        val arrangement = Arrangement().apply {
            selfTeamResult = Either.Right(TEAM_ID)
            isTeamMemberResult = Either.Right(true)
        }

        val result = arrangement.handler.handle(arrangement.transactionContext, event)

        assertEquals(
            listOf(
                "markDeleted",
                "deleteMembers",
                "updateCurrentCall",
                "protocol",
                "fetchUsers",
                "selfTeam",
                "isTeamMember",
                "persistMessage",
                "legalHold",
                "deleteMeetings",
            ),
            arrangement.callOrder,
        )
        assertEquals(Either.Right(Unit), result)
        assertEquals(listOf(TeamMembershipCall(event.removedList, TEAM_ID)), arrangement.teamMembershipCalls)
        assertEquals(memberRemovedFromTeamMessage(event), arrangement.persistedMessages.single())
    }

    @Test
    fun returnedMarkProtocolLeaveFetchAndPersistFailuresAreIgnoredButLegalHoldControlsFinalResult() = runTest {
        val event = memberLeaveEvent(MemberLeaveReason.UserDeleted)
        val arrangement = Arrangement().apply {
            markDeletedResult = Either.Left(StorageFailure.DataNotFound)
            protocolResult = Either.Right(MLS_PROTOCOL_INFO)
            leaveGroupResult = Either.Left(FAILURE)
            fetchUsersResult = Either.Left(FAILURE)
            selfTeamResult = Either.Left(FAILURE)
            persistMessageResult = Either.Left(FAILURE)
            legalHoldResult = Either.Left(FAILURE)
        }

        val result = arrangement.handler.handle(arrangement.transactionContext, event)

        assertEquals(Either.Left(FAILURE), result)
        assertEquals(
            listOf("markDeleted", "deleteMembers", "updateCurrentCall", "protocol", "leaveGroup", "fetchUsers", "selfTeam", "persistMessage", "legalHold"),
            arrangement.callOrder,
        )
    }

    @Test
    fun failedOrNullSelfTeamAndFailedMembershipResolveToRemovedWhileTrueResolvesToRemovedFromTeam() = runTest {
        val cases = listOf(
            TeamCase(Either.Left(FAILURE), Either.Right(true), MessageContent.MemberChange.Removed::class),
            TeamCase(Either.Right(null), Either.Right(true), MessageContent.MemberChange.Removed::class),
            TeamCase(Either.Right(TEAM_ID), Either.Left(StorageFailure.DataNotFound), MessageContent.MemberChange.Removed::class),
            TeamCase(Either.Right(TEAM_ID), Either.Right(true), MessageContent.MemberChange.RemovedFromTeam::class),
        )

        cases.forEach { case ->
            val arrangement = Arrangement().apply {
                selfTeamResult = case.teamResult
                isTeamMemberResult = case.membershipResult
            }
            arrangement.handler.handle(arrangement.transactionContext, memberLeaveEvent(MemberLeaveReason.UserDeleted))
            assertEquals(case.expectedContentClass, arrangement.persistedMessages.single().content::class)
        }
    }

    @Test
    fun givenSelfRemovedFromMlsConversationWithoutMlsContext_whenHandling_thenCleanupFailureIsIgnored() = runTest {
        val event = memberLeaveEvent(MemberLeaveReason.Removed)
        val arrangement = Arrangement().apply {
            transactionMlsContext = null
            protocolResult = Either.Right(MLS_PROTOCOL_INFO)
        }

        val result = arrangement.handler.handle(arrangement.transactionContext, event)

        assertEquals(Either.Right(Unit), result)
        assertTrue(arrangement.leaveGroupCalls.isEmpty())
        assertEquals(
            listOf("protocol", "fetchUsers", "persistMessage", "legalHold", "deleteMeetings"),
            arrangement.callOrder.takeLast(5),
        )
    }

    @Test
    fun ordinaryExceptionsAndCancellationPropagateByIdentityAtTheExactStage() = runTest {
        listOf(IllegalStateException("failed"), CancellationException("cancelled")).forEach { throwable ->
            val arrangement = Arrangement().apply {
                throwAt = "updateCurrentCall"
                thrown = throwable
            }

            val caught = catchThrowable { arrangement.handler.handle(arrangement.transactionContext, memberLeaveEvent(MemberLeaveReason.Left)) }

            assertSame(throwable, caught)
            assertEquals(listOf("deleteMembers", "updateCurrentCall"), arrangement.callOrder)
        }
    }

    private class Arrangement {
        val transactionContext = mock<CryptoTransactionContext>(MockMode.autoUnit)
        val mlsContext = mock<MlsCoreCryptoContext>(MockMode.autoUnit)
        var transactionMlsContext: MlsCoreCryptoContext? = mlsContext
        val conversationLifecycleEventRepository = mock<ConversationLifecycleEventRepository>()
        val userRepository = mock<MemberLeaveEventUserRepository>(MockMode.autoUnit)
        val conversationRepository = mock<ConversationProtocolGetter>()
        val persistMessageUseCase = mock<EventMessagePersistence>()
        val mlsConversationRepository = mock<MLSResetEventRepository>(MockMode.autoUnit)

        val callOrder = mutableListOf<String>()
        val markDeletedCalls = mutableListOf<List<UserId>>()
        val deleteMembersCalls = mutableListOf<DeleteMembersCall>()
        val updateCurrentCallCalls = mutableListOf<ConversationId>()
        val protocolCalls = mutableListOf<ConversationId>()
        val leaveGroupCalls = mutableListOf<LeaveGroupCall>()
        val fetchUsersCalls = mutableListOf<Set<UserId>>()
        var selfTeamCalls = 0
        val teamMembershipCalls = mutableListOf<TeamMembershipCall>()
        val persistedMessages = mutableListOf<Message.Standalone>()
        val legalHoldCalls = mutableListOf<ConversationId>()
        val deleteMeetingsCalls = mutableListOf<ConversationId>()

        var markDeletedResult: Either<StorageFailure, Unit> = Either.Right(Unit)
        var deleteMembersResult: Either<CoreFailure, Long> = Either.Right(1)
        var protocolResult: Either<CoreFailure, Conversation.ProtocolInfo> = Either.Right(Conversation.ProtocolInfo.Proteus)
        var leaveGroupResult: Either<CoreFailure, Unit> = Either.Right(Unit)
        var fetchUsersResult: Either<CoreFailure, Unit> = Either.Right(Unit)
        var selfTeamResult: Either<CoreFailure, TeamId?> = Either.Right(null)
        var isTeamMemberResult: Either<StorageFailure, Boolean> = Either.Right(false)
        var persistMessageResult: Either<CoreFailure, Unit> = Either.Right(Unit)
        var legalHoldResult: Either<CoreFailure, Unit> = Either.Right(Unit)
        var deleteMeetingsResult: Either<CoreFailure, Unit> = Either.Right(Unit)
        var throwAt: String? = null
        var thrown: Throwable? = null

        init {
            every { transactionContext.mls } calls { transactionMlsContext }
            everySuspend { userRepository.markAsDeleted(any()) } calls {
                callOrder += "markDeleted"
                @Suppress("UNCHECKED_CAST")
                markDeletedCalls += it.args[0] as List<UserId>
                throwIfConfigured("markDeleted")
                markDeletedResult
            }
            everySuspend { conversationLifecycleEventRepository.deleteMembers(any(), any()) } calls {
                callOrder += "deleteMembers"
                @Suppress("UNCHECKED_CAST")
                deleteMembersCalls += DeleteMembersCall(it.args[0] as List<UserId>, it.args[1] as ConversationId)
                throwIfConfigured("deleteMembers")
                deleteMembersResult
            }
            everySuspend { conversationRepository.getConversationProtocolInfo(any()) } calls {
                callOrder += "protocol"
                protocolCalls += it.args[0] as ConversationId
                throwIfConfigured("protocol")
                protocolResult
            }
            everySuspend { mlsConversationRepository.leaveGroup(any(), any()) } calls {
                callOrder += "leaveGroup"
                leaveGroupCalls += LeaveGroupCall(it.args[0] as MlsCoreCryptoContext, it.args[1] as GroupID)
                throwIfConfigured("leaveGroup")
                leaveGroupResult
            }
            everySuspend { userRepository.fetchUsersIfUnknownByIds(any()) } calls {
                callOrder += "fetchUsers"
                @Suppress("UNCHECKED_CAST")
                fetchUsersCalls += it.args[0] as Set<UserId>
                throwIfConfigured("fetchUsers")
                fetchUsersResult
            }
            everySuspend { userRepository.isAtLeastOneUserATeamMember(any(), any()) } calls {
                callOrder += "isTeamMember"
                @Suppress("UNCHECKED_CAST")
                teamMembershipCalls += TeamMembershipCall(it.args[0] as List<UserId>, it.args[1] as TeamId)
                throwIfConfigured("isTeamMember")
                isTeamMemberResult
            }
            everySuspend { persistMessageUseCase(any()) } calls {
                callOrder += "persistMessage"
                persistedMessages += it.args[0] as Message.Standalone
                throwIfConfigured("persistMessage")
                persistMessageResult
            }
        }

        val handler: MemberLeaveEventHandler = MemberLeaveEventHandlerImpl(
            conversationLifecycleEventRepository = conversationLifecycleEventRepository,
            userRepository = userRepository,
            conversationRepository = conversationRepository,
            persistMessage = persistMessageUseCase,
            updateConversationClientsForCurrentCall = { conversationId ->
                callOrder += "updateCurrentCall"
                updateCurrentCallCalls += conversationId
                throwIfConfigured("updateCurrentCall")
            },
            handleConversationMembersChanged = { conversationId ->
                callOrder += "legalHold"
                legalHoldCalls += conversationId
                throwIfConfigured("legalHold")
                legalHoldResult
            },
            selfTeamId = {
                callOrder += "selfTeam"
                selfTeamCalls += 1
                throwIfConfigured("selfTeam")
                selfTeamResult
            },
            mlsConversationRepository = mlsConversationRepository,
            deleteMeetingsByConversationId = { conversationId ->
                callOrder += "deleteMeetings"
                deleteMeetingsCalls += conversationId
                throwIfConfigured("deleteMeetings")
                deleteMeetingsResult
            },
            selfUserId = SELF_USER_ID,
        )

        private fun throwIfConfigured(operation: String) {
            if (throwAt == operation) throw checkNotNull(thrown)
        }
    }

    private companion object {
        val FAILURE = CoreFailure.MissingClientRegistration
        val SELF_USER_ID = UserId("self-userId", "domain")
        val USER_ID = UserId("userId", "domain")
        val CONVERSATION_ID = ConversationId("conversationId", "domain")
        val TEAM_ID = TeamId("teamId")
        val MLS_GROUP_ID = GroupID("group2")
        val MLS_PROTOCOL_INFO = Conversation.ProtocolInfo.MLS(
            groupId = MLS_GROUP_ID,
            groupState = Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED,
            epoch = 0UL,
            keyingMaterialLastUpdate = Instant.parse("2021-03-30T15:36:00.000Z"),
            cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519,
        )

        fun memberLeaveEvent(reason: MemberLeaveReason) = Event.Conversation.MemberLeave(
            id = "id",
            conversationId = CONVERSATION_ID,
            removedBy = SELF_USER_ID,
            removedList = listOf(SELF_USER_ID),
            dateTime = Instant.UNIX_FIRST_DATE,
            reason = reason,
        )

        fun memberRemovedMessage(event: Event.Conversation.MemberLeave) = Message.System(
            id = event.id,
            content = MessageContent.MemberChange.Removed(members = event.removedList),
            conversationId = event.conversationId,
            date = event.dateTime,
            senderUserId = event.removedBy,
            status = Message.Status.Sent,
            visibility = Message.Visibility.VISIBLE,
            expirationData = null,
        )

        fun memberRemovedFromTeamMessage(event: Event.Conversation.MemberLeave) = Message.System(
            id = event.id,
            content = MessageContent.MemberChange.RemovedFromTeam(members = event.removedList),
            conversationId = event.conversationId,
            date = event.dateTime,
            senderUserId = event.removedBy,
            status = Message.Status.Sent,
            visibility = Message.Visibility.VISIBLE,
            expirationData = null,
        )

        suspend fun catchThrowable(block: suspend () -> Unit): Throwable = try {
            block()
            fail("Expected failure")
        } catch (throwable: Throwable) {
            throwable
        }
    }

    private data class DeleteMembersCall(val userIds: List<UserId>, val conversationId: ConversationId)
    private data class LeaveGroupCall(val mlsContext: MlsCoreCryptoContext, val groupId: GroupID)
    private data class TeamMembershipCall(val userIds: List<UserId>, val teamId: TeamId)
    private data class TeamCase(
        val teamResult: Either<CoreFailure, TeamId?>,
        val membershipResult: Either<StorageFailure, Boolean>,
        val expectedContentClass: kotlin.reflect.KClass<out MessageContent.System>,
    )
}
