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

package com.wire.kalium.logic.di

import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCase
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.feature.channels.LegacyChannelsBindings
import com.wire.kalium.logic.feature.channels.LegacyChannelsDependencies
import com.wire.kalium.logic.feature.client.LegacyClientBindings
import com.wire.kalium.logic.feature.client.LegacyClientDependencies
import com.wire.kalium.logic.feature.conversation.ConversationScope
import com.wire.kalium.logic.feature.conversation.LegacyConversationBindings
import com.wire.kalium.logic.feature.conversation.LegacyConversationDependencies
import com.wire.kalium.logic.feature.debug.DebugScope
import com.wire.kalium.logic.feature.debug.LegacyDebugBindings
import com.wire.kalium.logic.feature.debug.LegacyDebugDependencies
import com.wire.kalium.logic.feature.meeting.LegacyMeetingBindings
import com.wire.kalium.logic.feature.meeting.LegacyMeetingDependencies
import com.wire.kalium.logic.feature.message.LegacyMessageBindings
import com.wire.kalium.logic.feature.message.LegacyMessageDependencies
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCase
import com.wire.kalium.logic.feature.search.FederatedSearchParser
import com.wire.kalium.logic.feature.search.LegacySearchBindings
import com.wire.kalium.logic.feature.search.LegacySearchDependencies
import com.wire.kalium.logic.feature.user.LegacyUserBindings
import com.wire.kalium.logic.feature.user.LegacyUserDependencies
import com.wire.kalium.logic.feature.user.UserScope
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.util.DebugKaliumApi
import com.wire.kalium.util.KaliumDispatcherImpl
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** Strict, unconfigured ports fail on unrelated resolution; all use cases come from production bindings. */
@OptIn(DebugKaliumApi::class)
class KaliumSessionApiIdentityTest {
    @Test
    fun oneGeneratedOwnerImplementsEightPublicContractsWithoutResolvingAnyCapture() {
        val graph = graph()
        val conversations: KaliumConversationApi = graph
        val messages: KaliumMessageApi = graph
        val clients: KaliumClientApi = graph
        val users: KaliumUserApi = graph
        val search: KaliumSearchApi = graph
        val channels: KaliumChannelsApi = graph
        val meetings: KaliumMeetingApi = graph
        val debug: KaliumDebugApi = graph
        listOf(conversations, messages, clients, users, search, channels, meetings, debug).forEach {
            assertSame(graph, it)
        }
    }

    @Test
    fun publicTypingUseCasesAndCompatibilityFacadeShareUserStateAcrossFreshWrappers() = runTest {
        var captures = 0
        val properties = mock<UserPropertyRepository>()
        everySuspend { properties.getTypingIndicatorStatus() } returns true
        val dependencies = object : LegacyConversationDependencies by mock<LegacyConversationDependencies>() {
            override val userPropertyRepository: UserPropertyRepository
                get() = properties.also { captures++ }
        }
        val first = graph(conversations = dependencies)
        val second = graph(conversations = dependencies)
        val api: KaliumConversationApi = first
        val facade = ConversationScope(first)
        val id = ConversationId("typing", TestUser.USER_ID.domain)
        val firstState = first.conversationTypingIndicatorIncomingRepository
        val secondState = second.conversationTypingIndicatorIncomingRepository
        firstState.addTypingUserInConversation(id, TestUser.USER_ID)
        secondState.addTypingUserInConversation(id, TestUser.USER_ID)
        assertNotSame(api.conversationClearUsersTypingEvents, api.conversationClearUsersTypingEvents)
        assertSame(firstState, facade.typingIndicatorIncomingRepository)
        assertNotSame(firstState, secondState)
        api.conversationClearUsersTypingEvents()
        assertEquals(emptySet(), firstState.observeUsersTyping(id).first())
        assertEquals(setOf(TestUser.USER_ID), secondState.observeUsersTyping(id).first())
        firstState.addTypingUserInConversation(id, TestUser.USER_ID)
        facade.clearUsersTypingEvents()
        assertEquals(emptySet(), firstState.observeUsersTyping(id).first())
        assertEquals(2, captures)
    }

    @Test
    fun publicConversationObserversKeepOneLiveParentCaptureAndFreshUseCases() = runTest {
        var captures = 0
        val unread = MutableStateFlow(1L)
        val repository = mock<ConversationRepository>()
        every { repository.observeUnreadArchivedConversationsCount() } returns unread
        val dependencies = object : LegacyConversationDependencies by mock<LegacyConversationDependencies>() {
            override val conversationRepository: ConversationRepository
                get() = repository.also { captures++ }
        }
        val api: KaliumConversationApi = graph(conversations = dependencies)
        val first = api.conversationObserveArchivedUnreadConversationsCount
        val second = api.conversationObserveArchivedUnreadConversationsCount
        assertNotSame(first, second)
        assertSame(unread, first())
        assertSame(first(), second())
        unread.value = 4L
        assertEquals(4L, second().first())
        assertEquals(1, captures)
    }

    @Test
    fun publicMessageGetterConstructsIndividuallyWithoutReadingRepositoryOrSendingPorts() {
        val api: KaliumMessageApi = graph()
        val first = api.messageDetectLinkPreviewTarget
        assertNotSame(first, api.messageDetectLinkPreviewTarget)
        assertNull(first("plain text"))
    }

    @Test
    fun publicClientObserversShareLiveParentStateWithoutResolvingOtherClientBindings() = runTest {
        var captures = 0
        val ids = MutableStateFlow<ClientId?>(null)
        val repository = mock<ClientRepository>()
        everySuspend { repository.observeCurrentClientId() } returns ids
        val dependencies = object : LegacyClientDependencies by mock<LegacyClientDependencies>() {
            override val clientRepository: ClientRepository
                get() = repository.also { captures++ }
        }
        val api: KaliumClientApi = graph(clients = dependencies)
        val first = api.clientObserveCurrentClientId
        val second = api.clientObserveCurrentClientId
        assertNotSame(first, second)
        assertSame(first(), second())
        ids.value = ClientId("registered")
        assertEquals(ids.value, second().first())
        assertEquals(1, captures)
    }

    @Test
    fun publicUserCaptureIsTheCompatibilityAliasAndSeparateOwnersCaptureSeparately() {
        var captures = 0
        val dependencies = object : LegacyUserDependencies by mock<LegacyUserDependencies>() {
            override val refreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase
                get() = mock<RefreshUsersWithoutMetadataUseCase>().also { captures++ }
        }
        val first = graph(users = dependencies)
        val second = graph(users = dependencies)
        val api: KaliumUserApi = first
        val other: KaliumUserApi = second
        assertSame(api.userRefreshUsersWithoutMetadata, api.userRefreshUsersWithoutMetadata)
        assertSame(api.userRefreshUsersWithoutMetadata, UserScope(first).refreshUsersWithoutMetadata)
        assertNotSame(api.userRefreshUsersWithoutMetadata, other.userRefreshUsersWithoutMetadata)
        assertEquals(2, captures)
    }

    @Test
    fun publicChannelGettersKeepDeferredFactoryResultsFreshWithinOneUser() {
        var captures = 0
        var creations = 0
        val dependencies = object : LegacyChannelsDependencies by mock<LegacyChannelsDependencies>() {
            override val conversationRepository: () -> ConversationRepository
                get() {
                    captures++
                    return { mock<ConversationRepository>().also { creations++ } }
                }
        }
        val first: KaliumChannelsApi = graph(channels = dependencies)
        val second: KaliumChannelsApi = graph(channels = dependencies)
        assertNotSame(first.channelsUpdateChannelAddPermission, first.channelsUpdateChannelAddPermission)
        assertEquals(1, captures)
        assertEquals(2, creations)
        second.channelsUpdateChannelAddPermission
        assertEquals(2, captures)
        assertEquals(3, creations)
    }

    @Test
    fun publicSearchParserCacheBelongsToEachFreshParserAndNotTheUserApi() = runTest {
        var captures = 0
        var federationReads = 0
        val dependencies = object : LegacySearchDependencies by mock<LegacySearchDependencies>() {
            override val selfUserId = TestUser.USER_ID
            override val sessionRepository: SessionRepository
                get() = mock<SessionRepository>().also { repository ->
                    captures++
                    everySuspend { repository.isFederated(TestUser.USER_ID) } calls {
                        federationReads++
                        true.right()
                    }
                }
        }
        val api: KaliumSearchApi = graph(search = dependencies)
        val first = api.searchFederatedSearchParser
        val second = api.searchFederatedSearchParser
        assertNotSame(first, second)
        val expected = FederatedSearchParser.Result("alice", "elsewhere.example")
        assertEquals(expected, first("alice@elsewhere.example", true))
        assertEquals(expected, first("alice@elsewhere.example", true))
        assertEquals(1, federationReads)
        assertEquals(expected, second("alice@elsewhere.example", true))
        assertEquals(2, federationReads)
        assertEquals(1, captures)
    }

    @Test
    fun publicMeetingUseCasesShareTheirParentCaptureButDifferentOwnersDoNot() {
        var captures = 0
        val dependencies = object : LegacyMeetingDependencies by mock<LegacyMeetingDependencies>() {
            override val dispatcher = KaliumDispatcherImpl
            override val meetingRepository: MeetingRepository
                get() = mock<MeetingRepository>().also { captures++ }
        }
        val first = graph(meetings = dependencies)
        val second = graph(meetings = dependencies)
        val api: KaliumMeetingApi = first
        assertNotSame(api.meetingObserveMeetingOccurrence, api.meetingObserveMeetingOccurrence)
        assertEquals(1, captures)
        val other: KaliumMeetingApi = second
        other.meetingObserveMeetingOccurrence
        assertEquals(2, captures)
        assertNotSame(first.meetingMeetingRepository, second.meetingMeetingRepository)
    }

    @Test
    fun publicDebugRepairRetainsTheStartupAliasAndSeparateOwnersRetainSeparateRepairs() {
        var captures = 0
        val dependencies = object : LegacyDebugDependencies by mock<LegacyDebugDependencies>() {
            override val conversationRepository: ConversationRepository
                get() = mock<ConversationRepository>().also { captures++ }
            override val userId = TestUser.USER_ID
            override val resetMLSConversationUseCase = mock<ResetMLSConversationUseCase>()
            override val transactionProvider = mock<CryptoTransactionProvider>()
        }
        val first = graph(debug = dependencies)
        val second: KaliumDebugApi = graph(debug = dependencies)
        val api: KaliumDebugApi = first
        assertSame(api.debugRepairFaultyRemovalKeysUseCase, api.debugRepairFaultyRemovalKeysUseCase)
        assertSame(api.debugRepairFaultyRemovalKeysUseCase, DebugScope(first).repairFaultyRemovalKeysUseCase)
        assertEquals(1, captures)
        assertNotSame(api.debugRepairFaultyRemovalKeysUseCase, second.debugRepairFaultyRemovalKeysUseCase)
        assertEquals(2, captures)
    }

    @Suppress("LongParameterList") // Test ports mirror the independent production binding families.
    private fun graph(
        conversations: LegacyConversationDependencies = mock(),
        messages: LegacyMessageDependencies = mock(),
        clients: LegacyClientDependencies = mock(),
        users: LegacyUserDependencies = mock(),
        search: LegacySearchDependencies = mock(),
        channels: LegacyChannelsDependencies = mock(),
        meetings: LegacyMeetingDependencies = mock(),
        debug: LegacyDebugDependencies = mock(),
    ): SessionIndividualApiTestGraph = createGraphFactory<SessionIndividualApiTestGraph.Factory>().create(
        conversations, messages, clients, users, search, channels, meetings, debug,
    )
}

/** Independent root avoids coupling API identity tests to the concurrently evolving session inputs. */
@DependencyGraph(UserLifetime::class)
internal interface SessionIndividualApiTestGraph :
    LegacyConversationBindings,
    LegacyMessageBindings,
    LegacyClientBindings,
    LegacyUserBindings,
    LegacySearchBindings,
    LegacyChannelsBindings,
    LegacyMeetingBindings,
    LegacyDebugBindings {
    @DependencyGraph.Factory
    interface Factory {
        @Suppress("LongParameterList") // The test root receives one deferred port for each family.
        fun create(
            @Provides conversations: LegacyConversationDependencies,
            @Provides messages: LegacyMessageDependencies,
            @Provides clients: LegacyClientDependencies,
            @Provides users: LegacyUserDependencies,
            @Provides search: LegacySearchDependencies,
            @Provides channels: LegacyChannelsDependencies,
            @Provides meetings: LegacyMeetingDependencies,
            @Provides debug: LegacyDebugDependencies,
        ): SessionIndividualApiTestGraph
    }
}
