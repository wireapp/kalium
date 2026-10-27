/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.meeting

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.MockConversation
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.ConversationDataSource
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.MeetingId
import com.wire.kalium.logic.data.meeting.MeetingDataSource
import com.wire.kalium.logic.feature.call.usecase.EndCallOnConversationChangeUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.EndCallUseCase
import com.wire.kalium.logic.feature.conversation.delete.DeleteConversationUseCaseImpl
import com.wire.kalium.logic.framework.TestCall
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.logic.util.stubs.ClientApiStub
import com.wire.kalium.logic.util.stubs.ConversationApiStub
import com.wire.kalium.network.api.base.authenticated.meeting.MeetingApi
import com.wire.kalium.network.api.model.QualifiedID
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.member.MemberEntity
import com.wire.kalium.persistence.dao.meeting.MeetingOccurrencesGenerator.GenerationLimit
import com.wire.kalium.persistence.dao.meeting.MeetingEntity
import com.wire.kalium.util.ConversationPersistenceApi
import com.wire.kalium.util.KaliumDispatcherImpl
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.time.Duration.Companion.seconds

// Real SQL repositories, scope wiring, deletion use case and call observer; HTTP and native crypto are seams.
@OptIn(ConversationPersistenceApi::class)
class DeleteMeetingForEveryoneIntegrationTest {
    private val fixtures = mutableListOf<Fixture>()

    @AfterTest
    fun closeDatabases() {
        fixtures.forEach {
            it.database.builder.close()
            it.database.delete()
        }
    }

    @Test
    fun creatorDeletionRemovesMeetingAndConversationAndEndsObservedCall() = runTest {
        val fixture = Fixture(testScheduler).also { fixtures += it }
        fixture.seed()
        backgroundScope.launch(fixture.dispatcher) { fixture.callObserver() }
        runCurrent()
        assertTrue(fixture.endedCalls.isEmpty())

        assertEquals(DeleteMeetingForEveryoneUseCase.Result.Success, fixture.scope.deleteMeetingForEveryone(MEETING_ID))
        runCurrent()

        assertNull(fixture.database.builder.meetingDao.getMeeting(MEETING_ENTITY.meetingId))
        assertEquals(Either.Left(StorageFailure.DataNotFound), fixture.conversations.getConversationById(CONVERSATION_ID))
        withContext(Dispatchers.Default) { withTimeout(5.seconds) { fixture.callEnded.await() } }
        assertEquals(listOf(CONVERSATION_ID), fixture.endedCalls)
        assertEquals(listOf("remote-delete", "crypto-start", "mls-leave", "crypto-end"), fixture.events)
    }

    @Test
    fun proteusMeetingDeletionUsesSameDatabaseAndObserverPathWithoutMlsWork() = runTest {
        val fixture = Fixture(testScheduler).also { fixtures += it }
        fixture.seed(protocol = ConversationEntity.ProtocolInfo.Proteus)
        backgroundScope.launch(fixture.dispatcher) { fixture.callObserver() }
        runCurrent()

        assertEquals(DeleteMeetingForEveryoneUseCase.Result.Success, fixture.scope.deleteMeetingForEveryone(MEETING_ID))
        runCurrent()

        withContext(Dispatchers.Default) { withTimeout(5.seconds) { fixture.callEnded.await() } }
        assertEquals(listOf(CONVERSATION_ID), fixture.endedCalls)
        assertEquals(listOf("remote-delete", "crypto-start", "crypto-end"), fixture.events)
    }

    @Test
    fun deletingMeetingAttachedToRegularGroupPreservesConversationAndCall() = runTest {
        val fixture = Fixture(testScheduler).also { fixtures += it }
        fixture.seed(type = ConversationEntity.Type.GROUP)
        backgroundScope.launch(fixture.dispatcher) { fixture.callObserver() }
        runCurrent()

        assertEquals(DeleteMeetingForEveryoneUseCase.Result.Success, fixture.scope.deleteMeetingForEveryone(MEETING_ID))
        runCurrent()

        assertNull(fixture.database.builder.meetingDao.getMeeting(MEETING_ENTITY.meetingId))
        fixture.conversations.getConversationById(CONVERSATION_ID).shouldSucceed()
        assertTrue(fixture.endedCalls.isEmpty())
        assertEquals(listOf("remote-delete"), fixture.events)
    }

    @Test
    fun failedRemoteDeletionPreservesMeetingConversationAndCall() = runTest {
        val fixture = Fixture(testScheduler).also { fixtures += it }
        fixture.seed()
        fixture.remoteFailure = true
        backgroundScope.launch(fixture.dispatcher) { fixture.callObserver() }
        runCurrent()

        assertIs<DeleteMeetingForEveryoneUseCase.Result.Failure>(fixture.scope.deleteMeetingForEveryone(MEETING_ID))
        runCurrent()

        assertNotNull(fixture.database.builder.meetingDao.getMeeting(MEETING_ENTITY.meetingId))
        fixture.conversations.getConversationById(CONVERSATION_ID).shouldSucceed()
        assertTrue(fixture.endedCalls.isEmpty())
        assertEquals(listOf("remote-delete"), fixture.events)
    }

    @Test
    fun failedMlsCleanupRetainsRemoteSuccessAndAlreadyAppliedLocalDeletion() = runTest {
        val fixture = Fixture(testScheduler).also { fixtures += it }
        fixture.seed()
        fixture.mlsFailure = true
        backgroundScope.launch(fixture.dispatcher) { fixture.callObserver() }
        runCurrent()

        assertEquals(DeleteMeetingForEveryoneUseCase.Result.Success, fixture.scope.deleteMeetingForEveryone(MEETING_ID))
        runCurrent()

        assertNull(fixture.database.builder.meetingDao.getMeeting(MEETING_ENTITY.meetingId))
        assertEquals(Either.Left(StorageFailure.DataNotFound), fixture.conversations.getConversationById(CONVERSATION_ID))
        withContext(Dispatchers.Default) { withTimeout(5.seconds) { fixture.callEnded.await() } }
        assertEquals(listOf(CONVERSATION_ID), fixture.endedCalls)
        assertEquals(listOf("remote-delete", "crypto-start", "mls-leave", "crypto-end"), fixture.events)
    }

    @Test
    fun missingMeetingDoesNotRequestRemoteDeletionOrRemoveConversation() = runTest {
        val fixture = Fixture(testScheduler).also { fixtures += it }
        fixture.seed()
        fixture.database.builder.meetingDao.deleteMeeting(MEETING_ENTITY.meetingId)

        val result = fixture.scope.deleteMeetingForEveryone(MEETING_ID)

        assertEquals(StorageFailure.DataNotFound, assertIs<DeleteMeetingForEveryoneUseCase.Result.Failure>(result).coreFailure)
        fixture.conversations.getConversationById(CONVERSATION_ID).shouldSucceed()
        assertTrue(fixture.events.isEmpty())
    }

    private class Fixture(scheduler: TestCoroutineScheduler) : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val dispatcher = StandardTestDispatcher(scheduler)
        val database = TestUserDatabase(TestUser.ENTITY_ID, dispatcher)
        val events = mutableListOf<String>()
        val endedCalls = mutableListOf<ConversationId>()
        val callEnded = CompletableDeferred<Unit>()
        var remoteFailure = false
        var mlsFailure = false
        private var inCryptoTransaction = false
        private val mlsRepository = mock<MLSConversationRepository>()
        private val api = mock<MeetingApi>()
        val conversations = ConversationDataSource(
            selfUserId = TestUser.USER_ID,
            conversationDAO = database.builder.conversationDAO,
            memberDAO = database.builder.memberDAO,
            conversationApi = ConversationApiStub(),
            messageDAO = database.builder.messageDAO,
            messageDraftDAO = database.builder.messageDraftDAO,
            clientDAO = database.builder.clientDAO,
            clientApi = ClientApiStub(),
            conversationMetaDataDAO = database.builder.conversationMetaDataDAO,
        )
        private val meetings = MeetingDataSource(
            selfUserId = TestUser.USER_ID,
            meetingDAO = database.builder.meetingDao,
            meetingApi = api,
            persistConversations = mock(),
            mlsConversationRepository = mlsRepository,
            conversationRepository = conversations,
            pendingActionsRepository = mock(),
            userRepository = mock(),
        )
        val scope = MeetingScope(
            dispatcher = KaliumDispatcherImpl,
            meetingRepository = meetings,
            userRepository = mock(),
            conversationRepository = conversations,
            refreshUsersWithoutMetadata = mock(),
            resetMLSConversation = mock(),
            joinExistingMLSConversation = mock(),
            leaveConversation = mock(),
            deleteConversation = DeleteConversationUseCaseImpl(conversations, mlsRepository),
            transactionProvider = cryptoTransactionProvider,
        )
        private val calls = mock<CallRepository>()
        private val endCall = mock<EndCallUseCase>()
        val callObserver = EndCallOnConversationChangeUseCaseImpl(calls, conversations, endCall, mock(mode = MockMode.autoUnit))

        init {
            every { calls.establishedCallsFlow() } returns MutableStateFlow(
                listOf(TestCall.oneOnOneEstablishedCall().copy(conversationId = CONVERSATION_ID))
            )
            everySuspend { endCall(CONVERSATION_ID) } calls {
                endedCalls += CONVERSATION_ID
                callEnded.complete(Unit)
                Unit
            }
            everySuspend { api.deleteMeeting(QualifiedID(MEETING_ID.value, MEETING_ID.domain)) } calls {
                assertFalse(inCryptoTransaction)
                assertNotNull(database.builder.meetingDao.getMeeting(MEETING_ENTITY.meetingId))
                conversations.getConversationById(CONVERSATION_ID).shouldSucceed()
                events += "remote-delete"
                if (remoteFailure) NetworkResponse.Error(TestNetworkException.generic)
                else NetworkResponse.Success(Unit, emptyMap(), 204)
            }
            everySuspend { cryptoTransactionProvider.transaction<Unit>("DeleteMeetingForEveryone", any()) } calls {
                assertNull(database.builder.meetingDao.getMeeting(MEETING_ENTITY.meetingId))
                events += "crypto-start"
                inCryptoTransaction = true
                try {
                    @Suppress("UNCHECKED_CAST")
                    val block = it.args[1] as suspend (CryptoTransactionContext) -> Either<CoreFailure, Unit>
                    block(transactionContext)
                } finally {
                    inCryptoTransaction = false
                    events += "crypto-end"
                }
            }
            everySuspend { mlsRepository.leaveGroup(mlsContext, any()) } calls {
                assertTrue(inCryptoTransaction)
                assertEquals(Either.Left(StorageFailure.DataNotFound), conversations.getConversationById(CONVERSATION_ID))
                events += "mls-leave"
                if (mlsFailure) Either.Left(CoreFailure.Unknown(null)) else Either.Right(Unit)
            }
        }

        suspend fun seed(
            type: ConversationEntity.Type = ConversationEntity.Type.MEETING,
            protocol: ConversationEntity.ProtocolInfo = ConversationEntity.ProtocolInfo.MLS(
                "meeting-group", ConversationEntity.GroupState.ESTABLISHED, 1UL, Instant.parse("2026-06-01T08:00:00Z"),
                ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
            ),
        ) {
            database.builder.userDAO.insertOrIgnoreIncompleteUsers(listOf(TestUser.ENTITY_ID))
            conversations.persistConversations(listOf(MockConversation.entity(CONVERSATION_ENTITY_ID).copy(type = type, protocolInfo = protocol)))
                .shouldSucceed()
            database.builder.memberDAO.insertMember(MemberEntity(TestUser.ENTITY_ID, MemberEntity.Role.Admin), CONVERSATION_ENTITY_ID)
            database.builder.meetingDao.upsertMeetings(
                listOf(MEETING_ENTITY), GenerationLimit.Window(MEETING_ENTITY.startTime, MEETING_ENTITY.endTime)
            )
        }
    }

    private companion object {
        val MEETING_ID = MeetingId("meeting-id", "domain")
        val CONVERSATION_ID = ConversationId("meeting-conversation", "domain")
        val CONVERSATION_ENTITY_ID = QualifiedIDEntity(CONVERSATION_ID.value, CONVERSATION_ID.domain)
        val MEETING_ENTITY = MeetingEntity(
            meetingId = QualifiedIDEntity(MEETING_ID.value, MEETING_ID.domain),
            conversationId = CONVERSATION_ENTITY_ID,
            creatorId = TestUser.ENTITY_ID,
            createdAt = Instant.parse("2026-06-01T08:00:00Z"),
            updatedAt = null,
            title = "Creator meeting",
            startTime = Instant.parse("2026-06-01T10:00:00Z"),
            endTime = Instant.parse("2026-06-01T11:00:00Z"),
            tzid = "Europe/Berlin",
            recurrence = null,
        )
    }
}
