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

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.auth.AccountTokens
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.session.StoreSessionParam
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.auth.ClearUserDataUseCase
import com.wire.kalium.logic.feature.auth.LogoutCallback
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import com.wire.kalium.logic.feature.auth.LogoutUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.ObserveEstablishedCallsUseCase
import com.wire.kalium.logic.feature.message.GetMessageByIdUseCase
import com.wire.kalium.logic.feature.session.DeleteSessionUseCase
import com.wire.kalium.logic.feature.session.DeregisterTokenUseCase
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAO
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.globalDatabaseProvider
import com.wire.kalium.usernetwork.di.PlatformUserAuthenticatedNetworkProvider
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KaliumConsumerConcurrencyTest {
    @Test
    fun waitingLogoutFromNonCancellablePublishedCreatorCompletesDeletionAndReopensGeneration() = runTest {
        assertTerminalLogout(nonCancellable = true)
    }

    @Test
    fun waitingLogoutFromCancellablePublishedCreatorPreservesFollowupAndReopensGeneration() = runTest {
        assertTerminalLogout(nonCancellable = false)
    }

    private suspend fun TestScope.assertTerminalLogout(nonCancellable: Boolean) {
        val fixture = TerminalLogoutFixture.create()
        val owner = SupervisorJob()
        val creator = CoroutineScope(owner + StandardTestDispatcher(testScheduler))
        val consumer = fixture.session.createConsumer()
        val observed = CompletableDeferred<Unit>()
        consumer.assemble(create = { owner }, onDiscard = {}, jobOnDiscard = { it })
        // This resolves the real SDK MessageDataSource and collects its real SQL-backed flow through
        // the consumer guard. Registry/provider removal must still cancel this resource operation.
        val reading = creator.launch {
            consumer.observeMessageById(TestConversation.ID, "absent").collect { observed.complete(Unit) }
        }
        val logout = fixture.logout(globalScope = this)
        try {
            observed.await()
            val terminal = creator.async {
                if (nonCancellable) {
                    withContext(NonCancellable) { fixture.logoutDeleteAndReopen(logout) }
                } else {
                    fixture.logoutDeleteAndReopen(logout)
                }
            }
            terminal.await()
            reading.join()
            assertTrue(reading.isCancelled)
            assertTrue(owner.isActive)
            assertFalse(terminal.isCancelled)
            assertFailsWith<CancellationException> { consumer.getMessageById(TestConversation.ID, "absent") }
        } finally {
            owner.cancel()
            fixture.close()
        }
    }

    @Test
    fun assemblyChecksGenerationInvalidationBeforeCancellationReachesItsConsumer() = runBlocking {
        // Inline cancellation lets the latch hold invalidate's first consumer while the second
        // checks the already-closed generation, before its own cancellation has been reached.
        val dispatchers = object : KaliumDispatcher by KaliumDispatcherImpl {
            override val io = Dispatchers.Unconfined
        }
        val dependencies = ConsumerTestDependencies(dispatchers)
        val cancellingFirst = CountDownLatch(1)
        val finishCancellation = CountDownLatch(1)
        dependencies.read = {
            try {
                awaitCancellation()
            } finally {
                cancellingFirst.countDown()
                check(finishCancellation.await(5, TimeUnit.SECONDS))
            }
        }
        val registry = KaliumConsumerRegistry(dependencies.userGraph())
        val first = registry.createConsumer()
        val second = registry.createConsumer()
        // Block cancellation of an admitted resource operation, not the published creator's job.
        val reading = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            first.getMessageById(TestConversation.ID, "message")
        }
        val threads = Executors.newSingleThreadExecutor()
        var invalidation: Future<*>? = null
        var discards = 0
        try {
            assertFailsWith<CancellationException> {
                second.assemble(
                    create = {
                        invalidation = threads.submit { registry.invalidate() }
                        assertTrue(cancellingFirst.await(5, TimeUnit.SECONDS))
                        Any()
                    },
                    onDiscard = { discards++ },
                )
            }
            assertEquals(1, discards)
            finishCancellation.countDown()
            invalidation!!.get(5, TimeUnit.SECONDS)
            reading.join()
            registry.invalidateAndJoin()
            assertEquals(0, dependencies.resources.closes)
        } finally {
            finishCancellation.countDown()
            threads.shutdownNow()
        }
    }

    @Test
    fun invalidationDuringExternalAssemblyWaitsForConstructionAndDiscardAndRejectsPublication() = runBlocking {
        val dependencies = ConsumerTestDependencies(KaliumDispatcherImpl)
        val registry = KaliumConsumerRegistry(dependencies.userGraph())
        // This is deliberately after registry.createConsumer has returned, like Android assembly.
        val consumer = registry.createConsumer()
        val entered = CountDownLatch(1)
        val finishConstruction = CountDownLatch(1)
        val discarding = CountDownLatch(1)
        val finishDiscard = CountDownLatch(1)
        val threads = Executors.newSingleThreadExecutor()
        try {
            val result = threads.submit(Callable {
                runCatching {
                    consumer.assemble(
                        create = {
                            entered.countDown()
                            check(finishConstruction.await(5, TimeUnit.SECONDS))
                            Any()
                        },
                        onDiscard = {
                            discarding.countDown()
                            check(finishDiscard.await(5, TimeUnit.SECONDS))
                        },
                    )
                }
            })
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            registry.invalidate()
            val drain = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { registry.invalidateAndJoin() }
            assertFalse(drain.isCompleted)
            finishConstruction.countDown()
            assertTrue(discarding.await(5, TimeUnit.SECONDS))
            assertFalse(drain.isCompleted)
            finishDiscard.countDown()
            assertTrue(result.get(5, TimeUnit.SECONDS).exceptionOrNull() is CancellationException)
            drain.await()
            assertTrue(dependencies.messages.isEmpty())
            assertTrue(dependencies.conversations.isEmpty())
            assertEquals(0, dependencies.resources.closes)
        } finally {
            finishConstruction.countDown()
            finishDiscard.countDown()
            threads.shutdownNow()
        }
    }

    @Test
    fun concurrentFirstResolutionConstructsOneRepositoryAndFreshUseCases() = runBlocking {
        val dependencies = ConsumerTestDependencies(KaliumDispatcherImpl)
        val registry = KaliumConsumerRegistry(dependencies.userGraph())
        val consumer = registry.createConsumer()
        val threads = Executors.newFixedThreadPool(12)
        try {
            val start = CyclicBarrier(12)
            val getters = (1..12).map {
                threads.submit(Callable<GetMessageByIdUseCase> {
                    start.await(5, TimeUnit.SECONDS)
                    consumer.getMessageById
                })
            }.map { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, dependencies.messages.size)
            getters.zipWithNext().forEach { (first, second) -> assertNotSame(first, second) }
        } finally {
            registry.invalidateAndJoin()
            threads.shutdownNow()
        }
    }

    @Test
    fun invalidationDuringCreationRejectsPublicationAndWaitsForConstructionToUnwind() = runBlocking {
        val dependencies = ConsumerTestDependencies(KaliumDispatcherImpl)
        val graph = dependencies.userGraph()
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val threads = Executors.newSingleThreadExecutor()
        val registry = KaliumConsumerRegistry { work ->
            entered.countDown()
            check(finish.await(5, TimeUnit.SECONDS))
            graph.consumerGraphFactory.create(work)
        }
        try {
            val result = threads.submit(Callable { runCatching { registry.createConsumer() } })
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            registry.invalidate()
            val drain = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { registry.invalidateAndJoin() }
            assertFalse(drain.isCompleted)
            assertFailsWith<IllegalStateException> { registry.createConsumer() }
            finish.countDown()
            assertTrue(result.get(5, TimeUnit.SECONDS).isFailure)
            drain.await()
            assertTrue(dependencies.messages.isEmpty())
            assertTrue(dependencies.conversations.isEmpty())
            assertEquals(0, dependencies.resources.closes)
        } finally {
            finish.countDown()
            threads.shutdownNow()
        }
    }
}

/** Real JVM providers/registry and logout orchestration; only external logout effects are stubbed. */
private class TerminalLogoutFixture private constructor(
    private val root: File,
    private val logic: CoreLogic,
    private val network: PlatformUserAuthenticatedNetworkProvider,
    private val userId: UserId,
) {
    private val provider = logic.userSessionScopeProvider.value
    val session = provider.getOrCreate(userId)
    private val storage = assertNotNull(logic.userStorageProvider.get(userId))
    private val apis = assertNotNull(network.get(userId.toApi()))
    private var cleared = false
    private var callbackCompleted = false

    suspend fun logout(globalScope: CoroutineScope): LogoutUseCase {
        val repository = mock<LogoutRepository>(mode = MockMode.autoUnit)
        everySuspend { repository.logout() } returns Either.Right(Unit)
        val deregister = mock<DeregisterTokenUseCase>()
        everySuspend { deregister() } returns DeregisterTokenUseCase.Result.Success
        val establishedCalls = mock<ObserveEstablishedCallsUseCase>()
        everySuspend { establishedCalls() } returns flowOf(emptyList())
        val clearUserData = mock<ClearUserDataUseCase>()
        everySuspend { clearUserData() } calls {
            assertFalse(session.coroutineContext.job.isActive)
            assertSame(storage, logic.userStorageProvider.get(userId))
            assertSame(apis, network.get(userId.toApi()))
            cleared = true
        }
        return LogoutUseCaseImpl(
            logoutRepository = repository,
            sessionRepository = logic.getGlobalScope().sessionRepository,
            clientRepository = mock(mode = MockMode.autoUnit),
            userConfigRepository = mock(mode = MockMode.autoUnit),
            userId = userId,
            sessionScope = session,
            deregisterTokenUseCase = deregister,
            clearClientDataUseCase = mock(mode = MockMode.autoUnit),
            clearUserDataUseCase = clearUserData,
            userSessionScopeProvider = provider,
            pushTokenRepository = mock(mode = MockMode.autoUnit),
            globalCoroutineScope = globalScope,
            userSessionWorkScheduler = mock(mode = MockMode.autoUnit),
            getEstablishedCallsUseCase = establishedCalls,
            endCallUseCase = mock(mode = MockMode.autoUnit),
            logoutCallback = object : LogoutCallback {
                override suspend fun invoke(userId: UserId, reason: LogoutReason) {
                    assertTrue(cleared)
                    assertNull(provider.get(userId))
                    assertNull(logic.userStorageProvider.get(userId))
                    assertNull(network.get(userId.toApi()))
                    callbackCompleted = true
                }
            },
            kaliumConfigs = KaliumConfigs(enableCalling = false),
            isNomadEnabled = { false },
        )
    }

    suspend fun logoutDeleteAndReopen(logout: LogoutUseCase) {
        logout(LogoutReason.SELF_HARD_LOGOUT, waitUntilCompletes = true)
        assertTrue(callbackCompleted)
        assertFailsWith<IllegalStateException> { session.createConsumer() }
        // The non-cancellable cancel-login follow-up deletes metadata; a subsequent login stores
        // credentials and acquires fresh provider instances for the same qualified user.
        assertIs<DeleteSessionUseCase.Result.Success>(logic.getGlobalScope().deleteSession(userId))
        val stored = provider.storeSession(
            StoreSessionParam(
                serverConfigId = SERVER_CONFIG_ID,
                ssoId = null,
                accountTokens = AccountTokens(userId, "access", "refresh", "Bearer", null),
                proxyCredentials = null,
                isPersistentWebSocketEnabled = false,
            ),
            logic.getGlobalScope().sessionRepository,
        )
        assertIs<Either.Right<Unit>>(stored)
        val reopened = provider.open(userId)
        assertNotSame(session, reopened)
        assertNotSame(storage, logic.userStorageProvider.get(userId))
        assertNotSame(apis, network.get(userId.toApi()))
        reopened.createConsumer().closeAndJoin()
    }

    suspend fun close() {
        try {
            provider.delete(userId)
        } finally {
            logic.getGlobalScope().coroutineContext.job.cancel()
            root.deleteRecursively()
        }
    }

    companion object {
        private const val SERVER_CONFIG_ID = "terminal-logout-server"
        private const val URL = "https://example.invalid"

        suspend fun create(): TerminalLogoutFixture {
            val root = Files.createTempDirectory("consumer-terminal-logout").toFile()
            val userId = UserId("terminal-creator", "example.com")
            val database = globalDatabaseProvider(
                platformDatabaseData = PlatformDatabaseData(StorageData.FileBacked(File(root, "global-storage"))),
                queriesContext = KaliumDispatcherImpl.io,
                passphrase = null,
            )
            try {
                database.serverConfigurationDAO.insert(
                    ServerConfigurationDAO.InsertData(
                        id = SERVER_CONFIG_ID, apiBaseUrl = URL, accountBaseUrl = URL, webSocketBaseUrl = "wss://example.invalid",
                        blackListUrl = URL, teamsUrl = URL, websiteUrl = URL, title = "Test", isOnPremises = false,
                        federation = false, domain = userId.domain, commonApiVersion = 8,
                        apiProxyHost = null, apiProxyNeedsAuthentication = null, apiProxyPort = null,
                    )
                )
                database.accountsDAO.insertOrReplace(UserIDEntity(userId.value, userId.domain), null, null, SERVER_CONFIG_ID, false)
            } finally {
                database.close()
            }
            val network = PlatformUserAuthenticatedNetworkProvider()
            val logic = CoreLogic(
                rootPath = root.absolutePath,
                kaliumConfigs = KaliumConfigs(enableCalling = false, mockedRequests = emptyList()),
                userAgent = "kalium-terminal-logout-test",
                userAuthenticatedNetworkProvider = network,
            )
            return TerminalLogoutFixture(root, logic, network, userId)
        }
    }
}
