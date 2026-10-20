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

import com.wire.kalium.cells.CellsScope
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.EndCallOnMLSResetUseCase
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.ProposalTimer
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.draft.MessageDraftRepository
import com.wire.kalium.logic.data.message.paging.NomadMessagePagingCoordinator
import com.wire.kalium.logic.data.mls.MLSMissingUsersMessageRejectionHandler
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionShutdown
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.sync.SyncStateObserver
import com.wire.kalium.messaging.hooks.CryptoStateChangeHookNotifier
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.userstorage.di.UserStorage
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
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

/** Production providers, with only real storage/network boundaries substituted. No old facade API. */
@DependencyGraph(UserLifetime::class)
internal interface UserSessionIdentityGraph : UserSessionConstructionBindings {
    @get:UserSessionBinding("conversationRepository")
    val conversations: ConversationRepository

    @get:UserSessionBinding("messageRepository")
    val messages: MessageRepository

    @get:UserSessionBinding("messageRepositoryFactory")
    val messageFactory: UserSessionMessageRepositoryFactory

    @get:UserSessionBinding("nomadMessagePagingCoordinator")
    val freshPaging: NomadMessagePagingCoordinator?

    @get:UserSessionBinding("messageApiPagingCoordinator")
    val apiPaging: NomadMessagePagingCoordinator?

    @get:UserSessionBinding("messageDraftRepository")
    val drafts: MessageDraftRepository

    @get:UserSessionBinding("slowSyncRepository")
    val slowSync: SlowSyncRepository

    @get:UserSessionBinding("incrementalSyncRepository")
    val incrementalSync: IncrementalSyncRepository

    @get:UserSessionBinding("userPropertyRepository")
    val properties: UserPropertyRepository

    @get:UserSessionBinding("mlsMissingUsersRejectionHandlerProvider")
    val missingUsers: UserSessionValue<() -> MLSMissingUsersMessageRejectionHandler>

    @get:UserSessionBinding("syncStateObserver")
    val syncObserver: UserSessionValue<Lazy<SyncStateObserver>>

    @DependencyGraph.Factory
    interface Factory {
        fun create(
            @Provides resources: UserSessionResourceInputs,
            @Provides state: UserSessionStateInputs,
            @Provides ownership: UserSessionOwnershipInputs,
            @Provides globals: UserSessionGlobalInputs,
        ): UserSessionIdentityGraph
    }
}

/** Request only this production slice; the borrowed calling repository is irrelevant to lazy acquisition. */
@DependencyGraph(UserLifetime::class)
internal interface UserSessionUnusedCallingGraph : UserSessionBindings4, UserSessionResourceBindings {
    @get:UserSessionBinding("callManager")
    val callingDelegate: UserSessionValue<Lazy<CallManager>>

    @get:UserSessionBinding("endCallOnMLSReset")
    val endCallOnReset: EndCallOnMLSResetUseCase

    @Provides
    @UserSessionBinding("callRepository")
    fun callingRepository(): CallRepository = mock()

    @DependencyGraph.Factory
    interface Factory {
        fun create(@Provides ownership: UserSessionOwnershipInputs): UserSessionUnusedCallingGraph
    }
}

class UserSessionBindingIdentityTest {
    @Test
    fun freshRepositoryReadsStayFreshAndBorrowTheSameActualResources() = runTest {
        withUserSessionBindingFixture { fixture ->
            val graph = fixture.graph()
            assertTrue(fixture.reads.isEmpty())
            assertNotSame(graph.conversations, graph.conversations)
            assertNotSame(graph.messages, graph.messages)
            // Construction reads real resource ports; none of the repository getters are port inputs.
            assertTrue(fixture.reads.contains("storage"))
            assertTrue(fixture.reads.contains("network"))
            assertSame(fixture.storage, fixture.resources.userStorage)
            assertSame(fixture.network, fixture.resources.authenticatedNetworkContainer)
        }
    }

    @Test
    fun userCachesRemainSharedAndNewGraphsReceiveFreshCaches() = runTest {
        withUserSessionBindingFixture { fixture ->
            val first = fixture.graph()
            val second = fixture.graph()
            assertSame(first.drafts, first.drafts)
            assertSame(first.slowSync, first.slowSync)
            assertSame(first.incrementalSync, first.incrementalSync)
            assertNotSame(first.drafts, second.drafts)
            assertNotSame(first.slowSync, second.slowSync)
            assertNotSame(first.incrementalSync, second.incrementalSync)
        }
    }

    @Test
    fun repeatedFactoryReadsInOneConstructorAreSeparateAndKeepArgumentOrder() = runTest {
        withUserSessionBindingFixture { fixture ->
            val graph = fixture.graph()
            val properties = graph.properties
            // Five separate UserConfigDataSource constructions each read two DAOs then config.
            // A single injected config captured for all five would collapse these reads.
            val expected = List(5) { listOf("network", "storage", "storage", "config") }.flatten() + "network"
            assertEquals(expected, fixture.reads)
            fixture.reads.clear()
            assertNotSame(properties, graph.properties)
            assertEquals(expected, fixture.reads)
        }
    }

    @Test
    fun deferredFunctionDoesNotResolveItsDependenciesAndEachInvocationStaysFresh() = runTest {
        withUserSessionBindingFixture { fixture ->
            val graph = fixture.graph()
            val function = graph.missingUsers.value
            assertSame(function, graph.missingUsers.value)
            assertTrue(fixture.reads.isEmpty())
            val first = function()
            val firstReads = fixture.reads.toList()
            assertTrue(firstReads.isNotEmpty())
            fixture.reads.clear()
            val second = function()
            assertNotSame(first, second)
            assertEquals(firstReads, fixture.reads)
        }
    }

    @Test
    fun rawLazyRetainsItsOwnDeferredInitializationAndIdentity() = runTest {
        withUserSessionBindingFixture { fixture ->
            val graph = fixture.graph()
            val observer = graph.syncObserver.value
            assertSame(observer, graph.syncObserver.value)
            assertFalse(observer.isInitialized())
            assertTrue(fixture.reads.isEmpty())
            val first = observer.value
            assertTrue(observer.isInitialized())
            val firstReads = fixture.reads.toList()
            assertTrue(firstReads.isNotEmpty())
            assertSame(first, graph.syncObserver.value.value)
            assertEquals(firstReads, fixture.reads)
        }
    }

    @Test
    fun factoryCoordinatorsRemainFreshWhileMessageApiAndConsumersCanBorrowOneUserCoordinator() = runTest {
        withUserSessionBindingFixture(nomadUrl = "https://nomad.example") { fixture ->
            val graph = fixture.graph()
            val retained = graph.apiPaging
            assertTrue(retained != null)
            assertSame(retained, graph.apiPaging)
            assertNotSame(graph.freshPaging, graph.freshPaging)
            assertNotSame(retained, graph.freshPaging)
            assertNotSame(graph.messageFactory.create(retained), graph.messageFactory.create(retained))
            assertNotSame(retained, fixture.graph().apiPaging)
        }
    }

    @Test
    fun bindingResolutionAndShutdownDoNotInitializeUnusedNativeOrCellsDelegates() = runTest {
        var callingAcquisitions = 0
        var cellsAcquisitions = 0
        val calling = lazy {
            callingAcquisitions++
            mock<CallManager>()
        }
        val cells = lazy<CellsScope> {
            cellsAcquisitions++
            error("Unused Cells owner was acquired")
        }
        val ownership = object : UserSessionOwnershipInputs by mock<UserSessionOwnershipInputs>() {
            override val callManager: Lazy<CallManager> get() = calling
        }
        val graph = createGraphFactory<UserSessionUnusedCallingGraph.Factory>().create(ownership)
        assertSame(graph.endCallOnReset, graph.endCallOnReset)
        assertSame(calling, graph.callingDelegate.value)
        assertFalse(calling.isInitialized())
        val consumers = KaliumConsumerRegistry { error("Unused consumer graph was constructed") }
        UserSessionShutdown(consumers, SupervisorJob(), cells, calling).stop()
        assertFalse(calling.isInitialized())
        assertFalse(cells.isInitialized())
        assertEquals(0, callingAcquisitions)
        assertEquals(0, cellsAcquisitions)
    }

    @Test
    fun initializedNativeDelegateKeepsItsIdentityAndIsDrainedByItsExistingOwner() = runTest {
        var acquisitions = 0
        var drains = 0
        val manager = mock<CallManager>()
        everySuspend { manager.cancelJobs() } calls { drains++ }
        val calling = lazy {
            acquisitions++
            manager
        }
        assertSame(manager, calling.value)
        val ownership = object : UserSessionOwnershipInputs by mock<UserSessionOwnershipInputs>() {
            override val callManager: Lazy<CallManager> get() = calling
        }
        val graph = createGraphFactory<UserSessionUnusedCallingGraph.Factory>().create(ownership)
        assertSame(calling, graph.callingDelegate.value)
        assertSame(manager, graph.callingDelegate.value.value)
        val cells = lazy<CellsScope> { error("Unused Cells owner was acquired") }
        val consumers = KaliumConsumerRegistry { error("Unused consumer graph was constructed") }
        UserSessionShutdown(consumers, SupervisorJob(), cells, calling).stop()
        assertEquals(1, acquisitions)
        assertEquals(1, drains)
        assertFalse(cells.isInitialized())
    }
}

private suspend fun TestScope.withUserSessionBindingFixture(
    nomadUrl: String? = null,
    block: suspend (UserSessionBindingFixture) -> Unit,
) {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val database = TestUserDatabase(UserIDEntity(Uuid.random().toString(), "user-session-binding.example"), dispatcher)
    val sessionJob = SupervisorJob(coroutineContext[Job])
    try {
        val scope = CoroutineScope(coroutineContext + dispatcher + sessionJob)
        block(UserSessionBindingFixture(database, scope, nomadUrl))
    } finally {
        withContext(NonCancellable) {
            // Drain every fixture-owned worker before closing its real persistence resources.
            sessionJob.cancelAndJoin()
            database.builder.close()
            database.delete()
        }
    }
}

private class UserSessionBindingFixture(
    database: TestUserDatabase,
    scope: CoroutineScope,
    nomadUrl: String? = null,
) {
    val reads = mutableListOf<String>()
    val storage = UserStorage(database.builder)
    val network = mock<AuthenticatedNetworkContainer>()
    private val configs = KaliumConfigs()
    private val proposals = MutableSharedFlow<ProposalTimer>()
    private val transactionMutex = Mutex()
    private val currentClient = mock<com.wire.kalium.logic.data.id.CurrentClientIdProvider>()

    init {
        every { network.conversationApi } returns mock()
        every { network.clientApi } returns mock()
        every { network.messageApi } returns mock()
        every { network.mlsMessageApi } returns mock()
        every { network.propertiesApi } returns mock()
        every { network.nomadDeviceSyncApi } returns mock()
        every { network.keyPackageApi } returns mock()
        every { network.mlsPublicKeyApi } returns mock()
    }

    val resources = object : UserSessionResourceInputs by mock<UserSessionResourceInputs>() {
        override val userStorage: UserStorage get() = storage.also { reads.add("storage") }
        override val authenticatedNetworkContainer: AuthenticatedNetworkContainer get() = network.also { reads.add("network") }
    }
    val state = object : UserSessionStateInputs by mock<UserSessionStateInputs>() {
        override val userId = UserId(database.userId.value, database.userId.domain)
        override val kaliumConfigs get() = configs.also { reads.add("config") }
        override val userScopedLogger get() = kaliumLogger
        override val nomadServiceUrl = nomadUrl
        override val proposalTimersFlow get() = proposals
        override val mlsMutex get() = transactionMutex
        override val currentCryptoStateChangeHookNotifier = CryptoStateChangeHookNotifier { }
        override val clientIdProvider get() = currentClient
    }
    val ownership = object : UserSessionOwnershipInputs by mock<UserSessionOwnershipInputs>() {
        override val sessionCoroutineScope = scope
    }

    fun graph(): UserSessionIdentityGraph = createGraphFactory<UserSessionIdentityGraph.Factory>().create(
        resources,
        state,
        ownership,
        mock<UserSessionGlobalInputs>(),
    )
}
