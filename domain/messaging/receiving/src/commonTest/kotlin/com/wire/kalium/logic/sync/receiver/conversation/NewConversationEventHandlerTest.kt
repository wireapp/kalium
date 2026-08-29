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
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.authenticated.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.authenticated.conversation.ReceiptMode
import com.wire.kalium.network.api.model.ConversationAccessRoleDTO
import com.wire.kalium.network.api.model.QualifiedID as NetworkQualifiedID
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import dev.mokkery.answering.calls
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NewConversationEventHandlerTest {

    @Test
    fun givenNewConversationOriginatedFromEvent_whenHandlingIt_thenPersistConversationShouldBeCalled() = runTest {
        val arrangement = Arrangement()
        val event = newConversationEvent()

        arrangement.handler.handle(arrangement.transactionContext, event)

        val persistCall = arrangement.persistCalls.single()
        assertSame(arrangement.transactionContext, persistCall.transactionContext)
        assertSame(event.conversation, persistCall.conversation)
        assertEquals(event.conversation.members.otherMembers.map { it.id.toModel() }.toSet(), arrangement.userRepository.fetchCalls.single())
    }

    @Test
    fun givenNewConversationEvent_whenHandlingIt_thenConversationLastModifiedShouldBeUpdated() = runTest {
        val arrangement = Arrangement()
        val event = newConversationEvent()
        val before = Clock.System.now()

        arrangement.handler.handle(arrangement.transactionContext, event)
        val after = Clock.System.now()

        val updateCall = arrangement.updateCalls.single()
        assertEquals(event.conversationId, updateCall.conversationId)
        assertTrue(updateCall.instant.toEpochMilliseconds() >= before.toEpochMilliseconds())
        assertTrue(updateCall.instant.toEpochMilliseconds() <= after.toEpochMilliseconds())
    }

    @Test
    fun givenNewGroupConversationEvent_whenHandlingIt_thenPersistTheSystemMessagesForNewConversation() = runTest {
        val arrangement = Arrangement()
        val event = newConversationEvent()

        arrangement.handler.handle(arrangement.transactionContext, event)

        with(arrangement.systemMessages) {
            assertEquals(WarningCall(event.conversation.id.toModel(), event.dateTime), warningCalls.single())
            assertEquals(StartedCall(event.senderUserId, event.conversation, event.dateTime), startedCalls.single())
            assertEquals(
                MembersCall(
                    event.conversationId.toDao(),
                    event.conversation.members.otherMembers.map { it.id.toModel() },
                    event.dateTime,
                ),
                membersCalls.single(),
            )
            assertEquals(ReceiptCall(event.conversation, event.dateTime), receiptCalls.single())
            assertEquals(
                AppsCall(
                    event.id,
                    event.conversationId,
                    hasAppsAccessEnabled = true,
                    event.senderUserId,
                    ConversationEntity.Type.GROUP,
                ),
                appsCalls.single(),
            )
            assertEquals(
                CellAccessCall(event.conversationId, event.conversation.teamId, false, event.dateTime),
                cellAccessCalls.single(),
            )
        }
    }

    @Test
    fun givenCellsReady_whenHandlingNewConversation_thenPersistEnabledCellAccessStatus() = runTest {
        val arrangement = Arrangement()
        val event = newConversationEvent(cellsState = "ready", teamId = "conversation-team")

        arrangement.handler.handle(arrangement.transactionContext, event)

        assertEquals(
            CellAccessCall(event.conversationId, "conversation-team", true, event.dateTime),
            arrangement.systemMessages.cellAccessCalls.single(),
        )
    }

    @Test
    fun givenNewGroupConversationEvent_whenHandlingItAndAlreadyPresent_thenShouldSkipPersistingTheSystemMessagesForNewConversation() =
        runTest {
            val arrangement = Arrangement().apply { persistResult = Either.Right(false) }

            arrangement.handler.handle(arrangement.transactionContext, newConversationEvent())

            assertTrue(arrangement.systemMessages.allCalls.isEmpty())
            assertEquals(listOf("selfTeam", "persist", "map#1", "update", "fetch"), arrangement.callOrder)
        }

    @Test
    fun givenNewGroupConversationEvent_whenHandlingIt_thenShouldSkipExecutingOneOnOneResolver() = runTest {
        val arrangement = Arrangement()

        arrangement.handler.handle(arrangement.transactionContext, newConversationEvent(ConversationResponse.Type.GROUP))

        assertTrue(arrangement.resolveCalls.isEmpty())
    }

    @Test
    fun givenNewOneOnOneConversationEvent_whenHandlingIt_thenShouldExecuteOneOnOneResolver() = runTest {
        val arrangement = Arrangement().apply {
            mappedTypes = listOf(ConversationEntity.Type.ONE_ON_ONE, ConversationEntity.Type.ONE_ON_ONE)
        }
        val event = newConversationEvent(ConversationResponse.Type.ONE_TO_ONE)

        arrangement.handler.handle(arrangement.transactionContext, event)

        val resolveCall = arrangement.resolveCalls.single()
        assertSame(arrangement.transactionContext, resolveCall.transactionContext)
        assertEquals(OTHER_USER_ID, resolveCall.userId)
        assertTrue(resolveCall.invalidateCurrentKnownProtocols)
    }

    @Test
    fun exactFullOrderArgumentsAndMapCallCountArePreserved() = runTest {
        val arrangement = Arrangement().apply {
            mappedTypes = listOf(ConversationEntity.Type.ONE_ON_ONE, ConversationEntity.Type.ONE_ON_ONE)
        }
        val event = newConversationEvent(ConversationResponse.Type.ONE_TO_ONE)

        arrangement.handler.handle(arrangement.transactionContext, event)

        assertEquals(
            listOf(
                "selfTeam",
                "persist",
                "map#1",
                "resolve",
                "update",
                "fetch",
                "warning",
                "started",
                "members",
                "receipt",
                "map#2",
                "apps",
                "cell",
            ),
            arrangement.callOrder,
        )
        assertEquals(2, arrangement.mapCalls.size)
        arrangement.mapCalls.forEach {
            assertSame(event.conversation, it.conversation)
            assertEquals(TEAM_ID, it.selfTeamId)
        }
        assertSame(arrangement.transactionContext, arrangement.persistCalls.single().transactionContext)
        assertSame(event.conversation, arrangement.persistCalls.single().conversation)
        assertEquals(setOf(OTHER_USER_ID, SECOND_OTHER_USER_ID), arrangement.userRepository.fetchCalls.single())
    }

    @Test
    fun selfTeamFailureIsIgnoredAndNullIsUsedForBothMappings() = runTest {
        val arrangement = Arrangement().apply { selfTeamResult = Either.Left(FAILURE) }

        arrangement.handler.handle(arrangement.transactionContext, newConversationEvent())

        assertEquals(2, arrangement.mapCalls.size)
        assertTrue(arrangement.mapCalls.all { it.selfTeamId == null })
        assertTrue(arrangement.systemMessages.appsCalls.isNotEmpty())
    }

    @Test
    fun returnedPersistUpdateFetchAndResolverFailuresShortCircuitLaterWork() = runTest {
        val cases = listOf(
            FailureCase(
                expectedOrder = listOf("selfTeam", "persist"),
                configure = { persistResult = Either.Left(FAILURE) },
            ),
            FailureCase(
                expectedOrder = listOf("selfTeam", "persist", "map#1", "update"),
                configure = { updateResult = Either.Left(StorageFailure.DataNotFound) },
            ),
            FailureCase(
                expectedOrder = listOf("selfTeam", "persist", "map#1", "update", "fetch"),
                configure = { userRepository.fetchResult = Either.Left(FAILURE) },
            ),
            FailureCase(
                expectedOrder = listOf("selfTeam", "persist", "map#1", "resolve"),
                configure = {
                    mappedTypes = listOf(ConversationEntity.Type.ONE_ON_ONE)
                    resolveResult = Either.Left(FAILURE)
                },
            ),
        )

        cases.forEach { case ->
            val arrangement = Arrangement().apply(case.configure)
            arrangement.handler.handle(arrangement.transactionContext, newConversationEvent())
            assertEquals(case.expectedOrder, arrangement.callOrder)
            assertTrue(arrangement.systemMessages.allCalls.isEmpty())
        }
    }

    @Test
    fun allSixMessageReturnedFailuresAreIgnoredIndependentlyAndOrderIsPreserved() = runTest {
        val expectedOrder = listOf("warning", "started", "members", "receipt", "apps", "cell")
        expectedOrder.forEach { failedOperation ->
            val arrangement = Arrangement().apply {
                systemMessages.results[failedOperation] = Either.Left(FAILURE)
            }

            arrangement.handler.handle(arrangement.transactionContext, newConversationEvent())

            assertEquals(expectedOrder, arrangement.systemMessages.allCalls, failedOperation)
            assertEquals("cell", arrangement.callOrder.last(), failedOperation)
        }
    }

    @Test
    fun mapperResultControlsGroupAndOneOnOneResolutionAndAppsType() = runTest {
        val group = Arrangement().apply {
            mappedTypes = listOf(ConversationEntity.Type.GROUP, ConversationEntity.Type.CHANNEL)
        }
        group.handler.handle(group.transactionContext, newConversationEvent(ConversationResponse.Type.ONE_TO_ONE))
        assertTrue(group.resolveCalls.isEmpty())
        assertSame(ConversationEntity.Type.CHANNEL, group.systemMessages.appsCalls.single().type)

        val oneOnOne = Arrangement().apply {
            mappedTypes = listOf(ConversationEntity.Type.ONE_ON_ONE, ConversationEntity.Type.MEETING)
        }
        oneOnOne.handler.handle(oneOnOne.transactionContext, newConversationEvent(ConversationResponse.Type.GROUP))
        assertEquals(1, oneOnOne.resolveCalls.size)
        assertSame(ConversationEntity.Type.MEETING, oneOnOne.systemMessages.appsCalls.single().type)
    }

    @Test
    fun exceptionsAndCancellationFromEveryDependencyMessageAndMappingPropagateByIdentityAndSkipLaterWork() = runTest {
        val stages = listOf(
            "selfTeam",
            "persist",
            "map#1",
            "resolve",
            "update",
            "fetch",
            "warning",
            "started",
            "members",
            "receipt",
            "map#2",
            "apps",
            "cell",
        )

        stages.forEach { stage ->
            listOf(IllegalStateException(stage), CancellationException(stage)).forEach { throwable ->
                val arrangement = Arrangement().apply {
                    mappedTypes = listOf(ConversationEntity.Type.ONE_ON_ONE, ConversationEntity.Type.ONE_ON_ONE)
                    throwAt = stage
                    thrown = throwable
                }

                val caught = catchThrowable {
                    arrangement.handler.handle(arrangement.transactionContext, newConversationEvent())
                }

                assertSame(throwable, caught, stage)
                assertEquals(stages.take(stages.indexOf(stage) + 1), arrangement.callOrder, stage)
            }
        }
    }

    private class Arrangement {
        val callOrder = mutableListOf<String>()
        val transactionContext = mock<CryptoTransactionContext>()
        val userRepository = RecordingUserRepository(callOrder)
        val systemMessages = RecordingSystemMessages(callOrder) { operation -> throwIfConfigured(operation) }
        val persistCalls = mutableListOf<PersistCall>()
        val resolveCalls = mutableListOf<ResolveCall>()
        val updateCalls = mutableListOf<UpdateCall>()
        val mapCalls = mutableListOf<MapCall>()
        private val conversationLifecycleEventRepository = mock<ConversationLifecycleEventRepository>()

        var selfTeamResult: Either<CoreFailure, TeamId?> = Either.Right(TEAM_ID)
        var persistResult: Either<CoreFailure, Boolean> = Either.Right(true)
        var resolveResult: Either<CoreFailure, ConversationId> = Either.Right(CONVERSATION_ID)
        var updateResult: Either<StorageFailure, Unit> = Either.Right(Unit)
        var mappedTypes: List<ConversationEntity.Type> = listOf(ConversationEntity.Type.GROUP, ConversationEntity.Type.GROUP)
        var throwAt: String? = null
        var thrown: Throwable? = null

        init {
            everySuspend {
                conversationLifecycleEventRepository.updateConversationModifiedDate(any(), any())
            } calls {
                callOrder += "update"
                @Suppress("UNCHECKED_CAST")
                updateCalls += UpdateCall(it.args[0] as ConversationId, it.args[1] as Instant)
                throwIfConfigured("update")
                updateResult
            }
            userRepository.beforeReturn = { throwIfConfigured("fetch") }
        }

        val handler = NewConversationEventHandlerImpl(
            conversationLifecycleEventRepository = conversationLifecycleEventRepository,
            userRepository = userRepository,
            selfTeamId = {
                callOrder += "selfTeam"
                throwIfConfigured("selfTeam")
                selfTeamResult
            },
            newGroupConversationSystemMessagesCreator = systemMessages,
            resolveOneOnOneWithUserId = { transactionContext, userId, invalidateCurrentKnownProtocols ->
                callOrder += "resolve"
                resolveCalls += ResolveCall(transactionContext, userId, invalidateCurrentKnownProtocols)
                throwIfConfigured("resolve")
                resolveResult
            },
            persistConversationFromEvent = { transactionContext, conversation ->
                callOrder += "persist"
                persistCalls += PersistCall(transactionContext, conversation)
                throwIfConfigured("persist")
                persistResult
            },
            mapConversationType = { conversation, selfTeamId ->
                val operation = "map#${mapCalls.size + 1}"
                callOrder += operation
                mapCalls += MapCall(conversation, selfTeamId)
                throwIfConfigured(operation)
                mappedTypes[mapCalls.lastIndex.coerceAtMost(mappedTypes.lastIndex)]
            },
        )

        private fun throwIfConfigured(operation: String) {
            if (throwAt == operation) throw checkNotNull(thrown)
        }
    }

    private class RecordingUserRepository(
        private val callOrder: MutableList<String>,
    ) : ConversationEventUserRepository {
        var fetchResult: Either<CoreFailure, Unit> = Either.Right(Unit)
        var beforeReturn: () -> Unit = {}
        val fetchCalls = mutableListOf<Set<UserId>>()

        override suspend fun fetchUsersIfUnknownByIds(ids: Set<UserId>): Either<CoreFailure, Unit> {
            callOrder += "fetch"
            fetchCalls += ids
            beforeReturn()
            return fetchResult
        }

        override suspend fun observeUser(userId: UserId): Flow<User?> = emptyFlow()
    }

    private class RecordingSystemMessages(
        private val callOrder: MutableList<String>,
        private val beforeReturn: (String) -> Unit,
    ) : NewConversationSystemMessagesCreator {
        val allCalls = mutableListOf<String>()
        val warningCalls = mutableListOf<WarningCall>()
        val startedCalls = mutableListOf<StartedCall>()
        val membersCalls = mutableListOf<MembersCall>()
        val receiptCalls = mutableListOf<ReceiptCall>()
        val appsCalls = mutableListOf<AppsCall>()
        val cellAccessCalls = mutableListOf<CellAccessCall>()
        val results: MutableMap<String, Either<CoreFailure, Unit>> = mutableMapOf(
            "warning" to Either.Right(Unit),
            "started" to Either.Right(Unit),
            "members" to Either.Right(Unit),
            "receipt" to Either.Right(Unit),
            "apps" to Either.Right(Unit),
            "cell" to Either.Right(Unit),
        )

        override suspend fun conversationStartedUnverifiedWarning(
            conversationId: ConversationId,
            instant: Instant,
        ): Either<CoreFailure, Unit> = record("warning") {
            warningCalls += WarningCall(conversationId, instant)
        }

        override suspend fun conversationStarted(
            creatorId: UserId,
            conversation: ConversationResponse,
            instant: Instant,
        ): Either<CoreFailure, Unit> = record("started") {
            startedCalls += StartedCall(creatorId, conversation, instant)
        }

        override suspend fun conversationResolvedMembersAdded(
            conversationId: ConversationIDEntity,
            validUsers: List<UserId>,
            instant: Instant,
        ): Either<CoreFailure, Unit> = record("members") {
            membersCalls += MembersCall(conversationId, validUsers, instant)
        }

        override suspend fun conversationReadReceiptStatus(
            conversation: ConversationResponse,
            instant: Instant,
        ): Either<CoreFailure, Unit> = record("receipt") {
            receiptCalls += ReceiptCall(conversation, instant)
        }

        override suspend fun conversationAppsAccessIfEnabled(
            eventId: String,
            conversationId: ConversationId,
            hasAppsAccessEnabled: Boolean,
            creatorId: UserId,
            type: ConversationEntity.Type,
        ): Either<CoreFailure, Unit> = record("apps") {
            appsCalls += AppsCall(eventId, conversationId, hasAppsAccessEnabled, creatorId, type)
        }

        override suspend fun conversationCellAccessStatus(
            conversationId: ConversationId,
            conversationTeamId: String?,
            isCellEnabled: Boolean,
            instant: Instant,
        ): Either<CoreFailure, Unit> = record("cell") {
            cellAccessCalls += CellAccessCall(conversationId, conversationTeamId, isCellEnabled, instant)
        }

        private inline fun record(operation: String, capture: () -> Unit): Either<CoreFailure, Unit> {
            callOrder += operation
            allCalls += operation
            capture()
            beforeReturn(operation)
            return checkNotNull(results[operation])
        }
    }

    private data class FailureCase(
        val expectedOrder: List<String>,
        val configure: Arrangement.() -> Unit,
    )

    private data class PersistCall(
        val transactionContext: CryptoTransactionContext,
        val conversation: ConversationResponse,
    )

    private data class ResolveCall(
        val transactionContext: CryptoTransactionContext,
        val userId: UserId,
        val invalidateCurrentKnownProtocols: Boolean,
    )

    private data class UpdateCall(val conversationId: ConversationId, val instant: Instant)
    private data class MapCall(val conversation: ConversationResponse, val selfTeamId: TeamId?)
    private data class WarningCall(val conversationId: ConversationId, val instant: Instant)
    private data class StartedCall(val creatorId: UserId, val conversation: ConversationResponse, val instant: Instant)
    private data class MembersCall(val conversationId: ConversationIDEntity, val validUsers: List<UserId>, val instant: Instant)
    private data class ReceiptCall(val conversation: ConversationResponse, val instant: Instant)
    private data class AppsCall(
        val eventId: String,
        val conversationId: ConversationId,
        val hasAppsAccessEnabled: Boolean,
        val creatorId: UserId,
        val type: ConversationEntity.Type,
    )
    private data class CellAccessCall(
        val conversationId: ConversationId,
        val conversationTeamId: String?,
        val isCellEnabled: Boolean,
        val instant: Instant,
    )

    private companion object {
        val CONVERSATION_ID = ConversationId("conversation", "example.com")
        val SENDER_USER_ID = UserId("sender", "example.com")
        val OTHER_USER_ID = UserId("other", "example.com")
        val SECOND_OTHER_USER_ID = UserId("second-other", "remote.example")
        val TEAM_ID = TeamId("team")
        val EVENT_INSTANT = Instant.parse("2024-01-02T03:04:05Z")
        val FAILURE = CoreFailure.Unknown(IllegalStateException("failure"))

        fun newConversationEvent(
            type: ConversationResponse.Type = ConversationResponse.Type.GROUP,
            cellsState: String? = null,
            teamId: String? = null,
        ): Event.Conversation.NewConversation {
            val response = ConversationResponse(
                creator = "sender@example.com",
                members = ConversationMembersResponse(
                    self = null,
                    otherMembers = listOf(
                        ConversationMemberDTO.Other(NetworkQualifiedID(OTHER_USER_ID.value, OTHER_USER_ID.domain), "wire_member"),
                        ConversationMemberDTO.Other(
                            NetworkQualifiedID(SECOND_OTHER_USER_ID.value, SECOND_OTHER_USER_ID.domain),
                            "wire_member",
                        ),
                    ),
                ),
                name = "conversation",
                id = NetworkQualifiedID(CONVERSATION_ID.value, CONVERSATION_ID.domain),
                groupId = null,
                epoch = null,
                type = type,
                messageTimer = null,
                teamId = teamId,
                protocol = ConvProtocol.PROTEUS,
                lastEventTime = EVENT_INSTANT.toString(),
                mlsCipherSuiteTag = null,
                access = emptySet(),
                accessRole = setOf(ConversationAccessRoleDTO.SERVICE),
                receiptMode = ReceiptMode.ENABLED,
                cellsState = cellsState,
            )
            return Event.Conversation.NewConversation(
                id = "event-id",
                conversationId = CONVERSATION_ID,
                senderUserId = SENDER_USER_ID,
                dateTime = EVENT_INSTANT,
                conversation = response,
            )
        }

        suspend fun catchThrowable(block: suspend () -> Unit): Throwable? = try {
            block()
            null
        } catch (throwable: Throwable) {
            throwable
        }
    }
}
