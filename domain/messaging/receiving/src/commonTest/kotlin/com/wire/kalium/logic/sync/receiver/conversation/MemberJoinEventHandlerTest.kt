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
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.MockConversation
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class MemberJoinEventHandlerTest {

    @Test
    fun givenMemberJoinEventWithSelfUser_whenHandlingIt_thenShouldFetchConversation() = runTest {
        val event = memberJoin(listOf(Conversation.Member(SELF_USER_ID, Conversation.Member.Role.Member)))
        val arrangement = Arrangement()

        arrangement.handler.handle(arrangement.transactionContext, event)

        val call = arrangement.fetchConversationCalls.single()
        assertSame(arrangement.transactionContext, call.transactionContext)
        assertEquals(event.conversationId, call.conversationId)
    }

    @Test
    fun givenMemberJoinEvent_whenHandlingIt_thenShouldPersistMembers() = runTest {
        val members = listOf(Conversation.Member(USER_ID, Conversation.Member.Role.Member))
        val event = memberJoin(members)
        val arrangement = Arrangement()

        arrangement.handler.handle(arrangement.transactionContext, event)

        assertEquals(listOf(PersistMembersCall(members, event.conversationId)), arrangement.persistMembersCalls)
    }

    @Test
    fun givenMemberJoinEventAndFetchConversationFails_whenHandlingIt_thenShouldAttemptPersistingMembersAnyway() = runTest {
        val members = listOf(Conversation.Member(USER_ID, Conversation.Member.Role.Member))
        val event = memberJoin(members)
        val arrangement = Arrangement().apply { fetchConversationResult = Either.Left(NO_NETWORK) }

        arrangement.handler.handle(arrangement.transactionContext, event)

        assertEquals(listOf(PersistMembersCall(members, event.conversationId)), arrangement.persistMembersCalls)
    }

    @Test
    fun givenMemberJoinEventInGroupConversation_whenHandlingIt_thenShouldPersistMemberChangeSystemMessage() = runTest {
        val event = memberJoin(listOf(Conversation.Member(SELF_USER_ID, Conversation.Member.Role.Admin)))
        val arrangement = Arrangement()

        arrangement.handler.handle(arrangement.transactionContext, event)

        assertTrue(arrangement.persistedMessages.single().content is MessageContent.MemberChange.Added)
        assertEquals(WarningCall(event.conversationId, event.dateTime), arrangement.warningCalls.single())
    }

    @Test
    fun givenMemberJoinEventIn1o1Conversation_whenHandlingIt_thenShouldNotPersistMemberChangeSystemMessage() = runTest {
        val event = memberJoin(listOf(Conversation.Member(USER_ID, Conversation.Member.Role.Admin)))
        val arrangement = Arrangement().apply { conversationResult = Either.Right(MockConversation.oneOnOne()) }

        arrangement.handler.handle(arrangement.transactionContext, event)

        assertTrue(arrangement.persistedMessages.isEmpty())
        assertTrue(arrangement.warningCalls.isEmpty())
        assertEquals(listOf(ActiveOneOnOneCall(USER_ID, event.conversationId)), arrangement.activeOneOnOneCalls)
    }

    @Test
    fun givenMemberJoinEventInSelfConversation_whenHandling_thenNoSystemMessageIsPersisted() = runTest {
        val conversation = MockConversation.self()
        val event = memberJoin(listOf(Conversation.Member(SELF_USER_ID, Conversation.Member.Role.Member)))
            .copy(conversationId = conversation.id)
        val arrangement = Arrangement().apply { conversationResult = Either.Right(conversation) }

        arrangement.handler.handle(arrangement.transactionContext, event)

        assertTrue(arrangement.persistedMessages.isEmpty())
        assertTrue(arrangement.warningCalls.isEmpty())
    }

    @Test
    fun givenMemberJoinEventInConnectionPendingConversation_whenHandling_thenNoSystemMessageIsPersisted() = runTest {
        val conversation = MockConversation.group().copy(type = Conversation.Type.ConnectionPending)
        val event = memberJoin(listOf(Conversation.Member(OTHER_USER_ID, Conversation.Member.Role.Member)))
            .copy(conversationId = conversation.id)
        val arrangement = Arrangement().apply { conversationResult = Either.Right(conversation) }

        arrangement.handler.handle(arrangement.transactionContext, event)

        assertTrue(arrangement.persistedMessages.isEmpty())
        assertTrue(arrangement.warningCalls.isEmpty())
    }

    @Test
    fun givenSelfMemberJoinEventInGroupConversation_whenHandlingIt_thenShouldPersistUnverifiedWarningSystemMessage() = runTest {
        val conversation = MockConversation.group()
        val event = memberJoin(listOf(Conversation.Member(SELF_USER_ID, Conversation.Member.Role.Admin))).copy(conversationId = conversation.id)
        val arrangement = Arrangement().apply { conversationResult = Either.Right(conversation) }

        arrangement.handler.handle(arrangement.transactionContext, event)

        assertEquals(WarningCall(conversation.id, event.dateTime), arrangement.warningCalls.single())
    }

    @Test
    fun givenOtherMemberJoinEventInGroupConversation_whenHandlingIt_thenShouldNotPersistUnverifiedWarningSystemMessage() = runTest {
        val event = memberJoin(listOf(Conversation.Member(OTHER_USER_ID, Conversation.Member.Role.Admin)))
        val arrangement = Arrangement()

        arrangement.handler.handle(arrangement.transactionContext, event)

        assertTrue(arrangement.warningCalls.isEmpty())
    }

    @Test
    fun givenSelfMemberJoinEventIn1o1Conversation_whenHandlingIt_thenShouldPersistUnverifiedWarningSystemMessage() = runTest {
        val conversation = MockConversation.oneOnOne()
        val event = memberJoin(listOf(Conversation.Member(SELF_USER_ID, Conversation.Member.Role.Admin))).copy(conversationId = conversation.id)
        val arrangement = Arrangement().apply { conversationResult = Either.Right(conversation) }

        arrangement.handler.handle(arrangement.transactionContext, event)

        assertEquals(WarningCall(conversation.id, event.dateTime), arrangement.warningCalls.single())
    }

    @Test
    fun givenOtherMemberJoinEventIn1o1Conversation_whenHandlingIt_thenShouldNotPersistUnverifiedWarningSystemMessage() = runTest {
        val conversation = MockConversation.oneOnOne()
        val event = memberJoin(listOf(Conversation.Member(OTHER_USER_ID, Conversation.Member.Role.Admin))).copy(conversationId = conversation.id)
        val arrangement = Arrangement().apply { conversationResult = Either.Right(conversation) }

        arrangement.handler.handle(arrangement.transactionContext, event)

        assertTrue(arrangement.warningCalls.isEmpty())
    }

    @Test
    fun givenMemberJoinEventIn1o1Conversation_whenHandlingIt_1o1ConversationForTheUserShouldBeSetIffItWasNotBefore() = runTest {
        val event = memberJoin(listOf(Conversation.Member(USER_ID, Conversation.Member.Role.Admin)))
        val arrangement = Arrangement().apply { conversationResult = Either.Right(MockConversation.oneOnOne()) }

        arrangement.handler.handle(arrangement.transactionContext, event)

        assertEquals(listOf(ActiveOneOnOneCall(USER_ID, event.conversationId)), arrangement.activeOneOnOneCalls)
    }

    @Test
    fun givenMemberJoinEventWithEmptyId_whenHandlingIt_thenShouldPersistSystemMessage() = runTest {
        val event = memberJoin(listOf(Conversation.Member(USER_ID, Conversation.Member.Role.Admin))).copy(id = "")
        val arrangement = Arrangement()

        arrangement.handler.handle(arrangement.transactionContext, event)

        assertNotEquals("", arrangement.persistedMessages.single().id)
    }

    @Test
    fun givenSelfUserReAddedToConversation_whenHandlingMemberJoinEvent_thenShouldClearDeletedLocallyFlag() = runTest {
        val event = memberJoin(listOf(Conversation.Member(SELF_USER_ID, Conversation.Member.Role.Member)))
        val arrangement = Arrangement()

        arrangement.handler.handle(arrangement.transactionContext, event)

        assertEquals(listOf(DeletedLocallyCall(event.conversationId, false)), arrangement.deletedLocallyCalls)
    }

    @Test
    fun givenOtherUserAddedToConversation_whenHandlingMemberJoinEvent_thenShouldNotClearDeletedLocallyFlag() = runTest {
        val event = memberJoin(listOf(Conversation.Member(OTHER_USER_ID, Conversation.Member.Role.Member)))
        val arrangement = Arrangement()

        arrangement.handler.handle(arrangement.transactionContext, event)

        assertTrue(arrangement.deletedLocallyCalls.isEmpty())
    }

    @Test
    fun givenMemberJoinEvent_whenHandlingIt_thenShouldUpdateConversationLegalHoldIfNeeded() = runTest {
        val event = memberJoin(listOf(Conversation.Member(USER_ID, Conversation.Member.Role.Admin)))
        val arrangement = Arrangement()

        arrangement.handler.handle(arrangement.transactionContext, event)

        assertEquals(listOf(event.conversationId), arrangement.legalHoldCalls)
    }

    @Test
    fun givenDrivePermissionsEnabledAndSelfJoinsGroup_thenPersistCellAccessStatus() = runTest {
        val conversation = MockConversation.group()
        val event = memberJoin(listOf(Conversation.Member(SELF_USER_ID, Conversation.Member.Role.Member)))
            .copy(conversationId = conversation.id)
        val arrangement = Arrangement(drivePermissionsEnabled = true).apply {
            conversationResult = Either.Right(conversation)
            isCellEnabledResult = Either.Right(true)
        }

        arrangement.handler.handle(arrangement.transactionContext, event)

        assertEquals(
            CellAccessCall(conversation.id, conversation.teamId?.value, true, event.dateTime),
            arrangement.cellAccessCalls.single(),
        )
    }

    @Test
    fun givenDrivePermissionsEnabledAndSelfJoinsGroup_whenHandlingEvent_thenShouldPersistCellAccessStatus() = runTest {
        val conversation = MockConversation.group()
        val event = memberJoin(listOf(Conversation.Member(SELF_USER_ID, Conversation.Member.Role.Member)))
            .copy(conversationId = conversation.id)
        val arrangement = Arrangement(drivePermissionsEnabled = true).apply {
            conversationResult = Either.Right(conversation)
            isCellEnabledResult = Either.Right(true)
        }

        arrangement.handler.handle(arrangement.transactionContext, event)

        assertEquals(
            CellAccessCall(conversation.id, conversation.teamId?.value, true, event.dateTime),
            arrangement.cellAccessCalls.single(),
        )
    }

    @Test
    fun givenDrivePermissionsDisabledAndSelfJoinsGroup_whenHandlingEvent_thenShouldNotPersistCellAccessStatus() = runTest {
        val conversation = MockConversation.group()
        val event = memberJoin(listOf(Conversation.Member(SELF_USER_ID, Conversation.Member.Role.Member)))
            .copy(conversationId = conversation.id)
        val arrangement = Arrangement(drivePermissionsEnabled = false).apply {
            conversationResult = Either.Right(conversation)
        }

        arrangement.handler.handle(arrangement.transactionContext, event)

        assertTrue(arrangement.cellAccessCalls.isEmpty())
    }

    @Test
    fun givenDrivePermissionsEnabledAndOtherUserJoinsGroup_whenHandlingEvent_thenShouldNotPersistCellAccessStatus() = runTest {
        val conversation = MockConversation.group()
        val event = memberJoin(listOf(Conversation.Member(OTHER_USER_ID, Conversation.Member.Role.Member)))
            .copy(conversationId = conversation.id)
        val arrangement = Arrangement(drivePermissionsEnabled = true).apply {
            conversationResult = Either.Right(conversation)
        }

        arrangement.handler.handle(arrangement.transactionContext, event)

        assertTrue(arrangement.cellAccessCalls.isEmpty())
    }

    @Test
    fun exactSuccessfulGroupOrderArgumentsAndMessageFieldsArePreserved() = runTest {
        val members = listOf(Conversation.Member(SELF_USER_ID, Conversation.Member.Role.Admin))
        val event = memberJoin(members)
        val arrangement = Arrangement()

        val result = arrangement.handler.handle(arrangement.transactionContext, event)

        assertEquals(
            listOf("fetchConversation", "fetchUsers", "setDeletedLocally", "persistMembers", "getConversation", "persistMessage", "warning", "legalHold"),
            arrangement.callOrder,
        )
        assertEquals(Either.Right(Unit), result)
        assertEquals(setOf(SELF_USER_ID), arrangement.fetchUsersCalls.single())
        assertEquals(PersistMembersCall(members, event.conversationId), arrangement.persistMembersCalls.single())
        val message = arrangement.persistedMessages.single() as Message.System
        assertEquals(event.id, message.id)
        assertEquals(MessageContent.MemberChange.Added(listOf(SELF_USER_ID)), message.content)
        assertEquals(event.conversationId, message.conversationId)
        assertEquals(event.dateTime, message.date)
        assertEquals(event.addedBy, message.senderUserId)
        assertEquals(Message.Status.Sent, message.status)
        assertEquals(Message.Visibility.VISIBLE, message.visibility)
        assertEquals(null, message.expirationData)
    }

    @Test
    fun returnedFailuresBeforeAndAfterMemberPersistenceAreIgnored() = runTest {
        val event = memberJoin(listOf(Conversation.Member(SELF_USER_ID, Conversation.Member.Role.Member)))
        val arrangement = Arrangement().apply {
            fetchConversationResult = Either.Left(NO_NETWORK)
            fetchUsersResult = Either.Left(FAILURE)
            setDeletedLocallyResult = Either.Left(FAILURE)
            conversationResult = Either.Left(StorageFailure.DataNotFound)
            legalHoldResult = Either.Left(FAILURE)
        }

        val result = arrangement.handler.handle(arrangement.transactionContext, event)

        assertEquals(Either.Right(Unit), result)
        assertEquals(listOf("fetchConversation", "fetchUsers", "setDeletedLocally", "persistMembers", "getConversation", "legalHold"), arrangement.callOrder)
    }

    @Test
    fun persistMembersResultOwnsTheMainChainAndSkipsAllFollowingWorkOnFailure() = runTest {
        val event = memberJoin(listOf(Conversation.Member(USER_ID, Conversation.Member.Role.Member)))
        val arrangement = Arrangement().apply { persistMembersResult = Either.Left(FAILURE) }

        val result = arrangement.handler.handle(arrangement.transactionContext, event)

        assertEquals(Either.Left(FAILURE), result)
        assertEquals(listOf("fetchConversation", "fetchUsers", "persistMembers"), arrangement.callOrder)
        assertTrue(arrangement.legalHoldCalls.isEmpty())
    }

    @Test
    fun returnedWarningMessagePersistenceAndOneOnOneActionFailuresAreIgnored() = runTest {
        val group = Arrangement().apply {
            warningResult = Either.Left(FAILURE)
            persistMessageResult = Either.Left(FAILURE)
            legalHoldResult = Either.Left(FAILURE)
        }
        val groupResult = group.handler.handle(
            group.transactionContext,
            memberJoin(listOf(Conversation.Member(SELF_USER_ID, Conversation.Member.Role.Member))),
        )
        assertEquals(Either.Right(Unit), groupResult)
        assertEquals(listOf("persistMessage", "warning", "legalHold"), group.callOrder.takeLast(3))

        val oneOnOne = Arrangement().apply {
            conversationResult = Either.Right(MockConversation.oneOnOne())
            activeOneOnOneResult = Either.Left(FAILURE)
        }
        val oneOnOneResult = oneOnOne.handler.handle(
            oneOnOne.transactionContext,
            memberJoin(listOf(Conversation.Member(USER_ID, Conversation.Member.Role.Member))),
        )
        assertEquals(Either.Right(Unit), oneOnOneResult)
        assertEquals(listOf("updateActiveOneOnOne", "legalHold"), oneOnOne.callOrder.takeLast(2))
    }

    @Test
    fun ordinaryExceptionsAndCancellationPropagateByIdentityAndSkipLaterWork() = runTest {
        listOf(IllegalStateException("failed"), CancellationException("cancelled")).forEach { throwable ->
            val arrangement = Arrangement().apply {
                throwAt = "fetchUsers"
                thrown = throwable
            }

            val caught = catchThrowable { arrangement.handler.handle(arrangement.transactionContext, memberJoin(emptyList())) }

            assertSame(throwable, caught)
            assertEquals(listOf("fetchConversation", "fetchUsers"), arrangement.callOrder)
        }
    }

    private class Arrangement(
        private val drivePermissionsEnabled: Boolean = false,
    ) {
        val transactionContext = mock<CryptoTransactionContext>()
        val conversationRepository = mock<ConversationEventLookupRepository>()
        val conversationLifecycleEventRepository = mock<ConversationLifecycleEventRepository>()
        val userRepository = mock<MemberJoinEventUserRepository>(MockMode.autoUnit)
        val persistMessageUseCase = mock<PersistMessageUseCase>()
        val newGroupConversationSystemMessagesCreator = mock<NewConversationSystemMessagesCreator>(MockMode.autoUnit)

        val callOrder = mutableListOf<String>()
        val fetchConversationCalls = mutableListOf<FetchConversationCall>()
        val fetchUsersCalls = mutableListOf<Set<UserId>>()
        val deletedLocallyCalls = mutableListOf<DeletedLocallyCall>()
        val persistMembersCalls = mutableListOf<PersistMembersCall>()
        val activeOneOnOneCalls = mutableListOf<ActiveOneOnOneCall>()
        val warningCalls = mutableListOf<WarningCall>()
        val persistedMessages = mutableListOf<Message.Standalone>()
        val legalHoldCalls = mutableListOf<ConversationId>()
        val cellAccessCalls = mutableListOf<CellAccessCall>()

        var fetchConversationResult: Either<CoreFailure, Unit> = Either.Right(Unit)
        var fetchUsersResult: Either<CoreFailure, Unit> = Either.Right(Unit)
        var setDeletedLocallyResult: Either<CoreFailure, Unit> = Either.Right(Unit)
        var persistMembersResult: Either<CoreFailure, Unit> = Either.Right(Unit)
        var conversationResult: Either<StorageFailure, Conversation> = Either.Right(MockConversation.group())
        var isCellEnabledResult: Either<StorageFailure, Boolean> = Either.Right(false)
        var activeOneOnOneResult: Either<CoreFailure, Unit> = Either.Right(Unit)
        var persistMessageResult: Either<CoreFailure, Unit> = Either.Right(Unit)
        var warningResult: Either<CoreFailure, Unit> = Either.Right(Unit)
        var legalHoldResult: Either<CoreFailure, Unit> = Either.Right(Unit)
        var throwAt: String? = null
        var thrown: Throwable? = null

        init {
            everySuspend { userRepository.fetchUsersIfUnknownByIds(any()) } calls {
                callOrder += "fetchUsers"
                @Suppress("UNCHECKED_CAST")
                fetchUsersCalls += it.args[0] as Set<UserId>
                throwIfConfigured("fetchUsers")
                fetchUsersResult
            }
            everySuspend { conversationLifecycleEventRepository.setConversationDeletedLocally(any(), any()) } calls {
                callOrder += "setDeletedLocally"
                deletedLocallyCalls += DeletedLocallyCall(it.args[0] as ConversationId, it.args[1] as Boolean)
                throwIfConfigured("setDeletedLocally")
                setDeletedLocallyResult
            }
            everySuspend { conversationLifecycleEventRepository.persistMembers(any(), any()) } calls {
                callOrder += "persistMembers"
                @Suppress("UNCHECKED_CAST")
                persistMembersCalls += PersistMembersCall(it.args[0] as List<Conversation.Member>, it.args[1] as ConversationId)
                throwIfConfigured("persistMembers")
                persistMembersResult
            }
            everySuspend { conversationRepository.getConversationById(any()) } calls {
                callOrder += "getConversation"
                throwIfConfigured("getConversation")
                conversationResult
            }
            everySuspend { conversationRepository.isCellEnabled(any()) } calls {
                callOrder += "isCellEnabled"
                throwIfConfigured("isCellEnabled")
                isCellEnabledResult
            }
            everySuspend { userRepository.updateActiveOneOnOneConversationIfNotSet(any(), any()) } calls {
                callOrder += "updateActiveOneOnOne"
                activeOneOnOneCalls += ActiveOneOnOneCall(it.args[0] as UserId, it.args[1] as ConversationId)
                throwIfConfigured("updateActiveOneOnOne")
                activeOneOnOneResult
            }
            everySuspend { newGroupConversationSystemMessagesCreator.conversationStartedUnverifiedWarning(any(), any()) } calls {
                callOrder += "warning"
                warningCalls += WarningCall(it.args[0] as ConversationId, it.args[1] as Instant)
                throwIfConfigured("warning")
                warningResult
            }
            everySuspend {
                newGroupConversationSystemMessagesCreator.conversationCellAccessStatus(any(), any(), any(), any())
            } calls {
                callOrder += "cellAccess"
                cellAccessCalls += CellAccessCall(
                    it.args[0] as ConversationId,
                    it.args[1] as String?,
                    it.args[2] as Boolean,
                    it.args[3] as Instant,
                )
                throwIfConfigured("cellAccess")
                Either.Right(Unit)
            }
            everySuspend { persistMessageUseCase(any()) } calls {
                callOrder += "persistMessage"
                persistedMessages += it.args[0] as Message.Standalone
                throwIfConfigured("persistMessage")
                persistMessageResult
            }
        }

        val handler: MemberJoinEventHandler = MemberJoinEventHandlerImpl(
            conversationRepository = conversationRepository,
            conversationLifecycleEventRepository = conversationLifecycleEventRepository,
            userRepository = userRepository,
            persistMessage = persistMessageUseCase,
            handleConversationMembersChanged = { conversationId ->
                callOrder += "legalHold"
                legalHoldCalls += conversationId
                throwIfConfigured("legalHold")
                legalHoldResult
            },
            newGroupConversationSystemMessagesCreator = newGroupConversationSystemMessagesCreator,
            selfUserId = SELF_USER_ID,
            fetchConversation = { transactionContext, conversationId ->
                callOrder += "fetchConversation"
                fetchConversationCalls += FetchConversationCall(transactionContext, conversationId)
                throwIfConfigured("fetchConversation")
                fetchConversationResult
            },
            drivePermissionsEnabled = drivePermissionsEnabled,
        )

        private fun throwIfConfigured(operation: String) {
            if (throwAt == operation) throw checkNotNull(thrown)
        }
    }

    private companion object {
        val SELF_USER_ID = UserId("self-user", "domain")
        val USER_ID = UserId("user", "domain")
        val OTHER_USER_ID = UserId("other-user", "domain")
        val FAILURE = CoreFailure.MissingClientRegistration
        val NO_NETWORK = NetworkFailure.NoNetworkConnection(null)

        fun memberJoin(members: List<Conversation.Member>) = Event.Conversation.MemberJoin(
            id = "eventId",
            conversationId = MockConversation.ID,
            addedBy = USER_ID,
            members = members,
            dateTime = Instant.UNIX_FIRST_DATE,
        )

        suspend fun catchThrowable(block: suspend () -> Unit): Throwable = try {
            block()
            fail("Expected failure")
        } catch (throwable: Throwable) {
            throwable
        }
    }

    private data class FetchConversationCall(val transactionContext: CryptoTransactionContext, val conversationId: ConversationId)
    private data class DeletedLocallyCall(val conversationId: ConversationId, val deletedLocally: Boolean)
    private data class PersistMembersCall(val members: List<Conversation.Member>, val conversationId: ConversationId)
    private data class ActiveOneOnOneCall(val userId: UserId, val conversationId: ConversationId)
    private data class WarningCall(val conversationId: ConversationId, val instant: Instant)
    private data class CellAccessCall(
        val conversationId: ConversationId,
        val conversationTeamId: String?,
        val isCellEnabled: Boolean,
        val instant: Instant,
    )
}
