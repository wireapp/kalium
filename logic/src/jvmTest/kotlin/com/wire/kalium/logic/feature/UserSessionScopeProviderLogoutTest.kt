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
package com.wire.kalium.logic.feature

import com.wire.kalium.logic.data.auth.AccountInfo
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.session.SessionDataSource
import com.wire.kalium.logic.feature.session.CurrentSessionFlowUseCase
import com.wire.kalium.logic.feature.session.CurrentSessionResult
import com.wire.kalium.persistence.client.AuthTokenEntity
import com.wire.kalium.persistence.client.AuthTokenStorage
import com.wire.kalium.persistence.client.ProxyCredentialsEntity
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.auth.AccountTokens
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.session.StoreSessionParam
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAO
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.GlobalDatabaseBuilder
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.globalDatabaseProvider
import com.wire.kalium.usernetwork.di.PlatformUserAuthenticatedNetworkProvider
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentLinkedQueue
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

@Suppress("LargeClass") // Share the existing real-storage/worker teardown fixture across lifecycle barrier cases.
class UserSessionScopeProviderLogoutTest {

    // Custom registries and retired generations no longer appear in CoreLogic's provider cache.
    // Retain the actual owners until their jobs have completed and their database can be closed.
    private val fixtureScopes = ConcurrentLinkedQueue<UserSessionScope>()
    private val fixtureDatabases = ConcurrentLinkedQueue<UserDatabaseBuilder>()
    private val fixtureGlobalDatabases = ConcurrentLinkedQueue<GlobalDatabaseBuilder>()
    private val fixtureGlobalJobs = ConcurrentLinkedQueue<Job>()
    private val fixtureProviders = mutableListOf<UserSessionScopeProvider>()

    @Test
    fun constructionDefersRuntimeUntilTheRegistryRetainsThePrivateOwner() = runTest {
        val userId = UserId("prepared-runtime", "example.com")
        withAccounts(userId) { logic, network ->
            var starts = 0
            lateinit var provider: UserSessionScopeProviderCommon
            provider = commonProvider(logic, startScope = { scope ->
                assertNull(provider.get(userId))
                assertFalse(scope.syncStateObserver.isInitialized(), "Construction started sync-state observation")
                assertNull(initializedKeyPackageJob(scope), "Construction started detached key-package work")
                assertTrue(scope.coroutineContext.job.children.none(), "Construction launched account workers")
                assertFalse(scope.isCellsInitialized)
                starts++
                scope.startRuntime()
                assertTrue(scope.syncStateObserver.isInitialized(), "Explicit startup skipped sync-state observation")
                assertNotNull(initializedKeyPackageJob(scope), "Explicit startup skipped key-package work")
                assertTrue(scope.coroutineContext.job.children.any(), "Explicit startup did not launch workers")
            }, removeNetwork = { network.remove(it.toApi()) })
            val owner = provider.getOrCreate(userId)
            assertSame(owner, provider.getOrCreate(userId))
            assertSame(owner, provider.get(userId))
            assertEquals(1, starts)
        }
    }

    @Test
    fun givenAccountWorkDoesNotStop_whenLoggingOut_thenLocalDataIsStillCleared() = runTest {
        val userId = UserId("user", "example.com")
        withAccounts(userId) { logic, _ ->
            val provider = retainingProvider(logic)
            val scope = provider.getOrCreate(userId)
            val release = CompletableDeferred<Unit>()
            val entered = CompletableDeferred<Unit>()
            val stuckWorker = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                withContext(NonCancellable) {
                    entered.complete(Unit)
                    release.await()
                }
            }
            var cleared = false
            try {
                entered.await()
                assertTrue(provider.logout(userId, scope, prepare = {}, clearLocalData = { cleared = true }))
                assertTrue(cleared)
                assertFalse(scope.isCellsInitialized)
                assertNull(provider.get(userId))
            } finally {
                release.complete(Unit)
                stuckWorker.join()
            }
        }
    }

    @Test
    fun callerTimeoutDuringCleanupRetainsTheClosingGenerationUntilRetry() = runTest {
        val userId = UserId("caller-timeout", "example.com")
        withAccounts(userId) { logic, network ->
            var removed = false
            val provider = commonProvider(logic, startScope = {}, removeNetwork = {
                removed = true
                network.remove(it.toApi())
            })
            val outgoing = provider.getOrCreate(userId)
            val storage = assertNotNull(logic.userStorageProvider.get(userId))
            val apis = assertNotNull(network.get(userId.toApi()))
            val finishUnwind = CompletableDeferred<Unit>()
            val worker = outgoing.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) { finishUnwind.await() }
                }
            }
            try {
                val deletion = async { runCatching { withTimeout(1_000) { provider.delete(userId) } } }
                advanceTimeBy(1_001)
                runCurrent()
                assertIs<TimeoutCancellationException>(deletion.await().exceptionOrNull())
                assertFalse(removed)
                assertFalse(worker.isCompleted)
                assertSame(storage, logic.userStorageProvider.get(userId))
                assertSame(apis, network.get(userId.toApi()))
                assertNull(provider.get(userId))
                assertFailsWith<IllegalStateException> { provider.getOrCreate(userId) }
                finishUnwind.complete(Unit)
                withContext(Dispatchers.Default) {
                    worker.join()
                    provider.delete(userId)
                }
                assertTrue(removed)
                assertNull(logic.userStorageProvider.get(userId))
                assertNull(network.get(userId.toApi()))
                assertNotSame(outgoing, provider.getOrCreate(userId))
            } finally {
                finishUnwind.complete(Unit)
                withContext(NonCancellable + Dispatchers.Default) {
                    worker.join()
                    provider.delete(userId)
                }
            }
        }
    }

    @Test
    fun givenInitializedCells_whenLoggingOut_thenCellsWorkDrainsBeforeLocalDataIsCleared() = runTest {
        val userId = UserId("user", "example.com")
        withAccounts(userId) { logic, _ ->
            val provider = retainingProvider(logic)
            val scope = provider.getOrCreate(userId)
            val cells = scope.cells
            val worker = cells.launch(start = CoroutineStart.UNDISPATCHED) { awaitCancellation() }
            try {
                withContext(Dispatchers.Default) {
                    assertTrue(provider.logout(userId, scope, prepare = {}, clearLocalData = {
                        assertTrue(worker.isCompleted)
                        assertTrue(cells.coroutineContext.job.isCompleted)
                    }))
                }
                assertNull(provider.get(userId))
            } finally {
                worker.cancelAndJoin()
            }
        }
    }

    @Test
    fun failedSynchronousStartupRetainsWorkerAndProvidersUntilExplicitCleanupDrains() = runTest {
        checkFailedStartupCleanup(IllegalStateException("Synchronous startup failed after launching workers"))
    }

    @Test
    fun cancelledSynchronousStartupRetainsWorkerAndProvidersUntilExplicitCleanupDrains() = runTest {
        checkFailedStartupCleanup(CancellationException("Synchronous startup was cancelled after launching workers"))
    }

    private suspend fun TestScope.checkFailedStartupCleanup(failure: Throwable) {
        val userId = UserId("failed-startup", "example.com")
        withAccounts(userId) { logic, network ->
            val starting = CompletableDeferred<UserSessionScope>()
            val startedWorker = CompletableDeferred<Job>()
            val unwinding = CompletableDeferred<Unit>()
            val finishUnwind = CompletableDeferred<Unit>()
            var starts = 0
            var removed = false
            val provider = commonProvider(logic, startScope = { scope ->
                scope.startRuntime()
                starts++
                if (starts == 1) {
                    starting.complete(scope)
                    startedWorker.complete(scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        try {
                            awaitCancellation()
                        } finally {
                            withContext(NonCancellable) {
                                unwinding.complete(Unit)
                                finishUnwind.await()
                            }
                        }
                    })
                    throw failure
                }
            }, removeNetwork = {
                if (startedWorker.isCompleted) assertTrue(startedWorker.await().isCompleted)
                removed = true
                network.remove(it.toApi())
            })
            try {
                assertSame(failure, runCatching { provider.getOrCreate(userId) }.exceptionOrNull())
                val outgoing = starting.await()
                val worker = startedWorker.await()
                unwinding.await()
                val storage = assertNotNull(logic.userStorageProvider.get(userId))
                val apis = assertNotNull(network.get(userId.toApi()))
                assertFalse(outgoing.coroutineContext.job.isActive)
                assertFalse(outgoing.coroutineContext.job.isCompleted)
                assertNull(provider.get(userId))
                assertFailsWith<IllegalStateException> { provider.getOrCreate(userId) }
                val timedOutDeletion = async { runCatching { provider.delete(userId) } }
                advanceTimeBy(3_001)
                runCurrent()
                assertIs<TimeoutCancellationException>(timedOutDeletion.await().exceptionOrNull())
                assertFalse(removed)
                assertSame(storage, logic.userStorageProvider.get(userId))
                assertSame(apis, network.get(userId.toApi()))
                // A cancelled cleanup keeps the same private owner and cannot admit another startup.
                val deletion = async(start = CoroutineStart.UNDISPATCHED) { provider.delete(userId) }
                deletion.cancelAndJoin()
                assertNull(provider.get(userId))
                assertFailsWith<IllegalStateException> { provider.getOrCreate(userId) }
                assertEquals(1, starts)
                assertSame(storage, logic.userStorageProvider.get(userId))
                assertFalse(worker.isCompleted)
                finishUnwind.complete(Unit)
                // Other real Default-dispatcher startup jobs also need wall-clock time to unwind;
                // virtual time is used only for the deliberately blocked timeout assertion above.
                withContext(Dispatchers.Default) {
                    worker.join()
                    provider.delete(userId)
                }
                assertTrue(removed)
                assertTrue(outgoing.coroutineContext.job.isCompleted)
                assertNull(logic.userStorageProvider.get(userId))
                assertNull(network.get(userId.toApi()))
                val reopened = provider.getOrCreate(userId)
                assertNotSame(outgoing, reopened)
                assertEquals(2, starts)
                assertSame(reopened, provider.get(userId))
            } finally {
                finishUnwind.complete(Unit)
                withContext(NonCancellable + Dispatchers.Default) { provider.delete(userId) }
            }
        }
    }

    @Test
    fun consumerAssemblyStillDrainsBeforeProviderRemovalAfterTheLegacyWorkerTimeout() = runTest {
        val userId = UserId("slow-consumer", "example.com")
        withAccounts(userId) { logic, network ->
            var removed = false
            val provider = commonProvider(logic, removeNetwork = {
                removed = true
                network.remove(it.toApi())
            })
            val scope = provider.getOrCreate(userId)
            val storage = logic.userStorageProvider.get(userId)
            val consumer = scope.createConsumer()
            val entered = CompletableDeferred<Unit>()
            val finishAssembly = CountDownLatch(1)
            val releaseWorker = CompletableDeferred<Unit>()
            val legacyWorker = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                withContext(NonCancellable) { releaseWorker.await() }
            }
            val thread = Executors.newSingleThreadExecutor()
            try {
                val assembly = thread.submit(Callable {
                    runCatching {
                        consumer.assemble(create = {
                            entered.complete(Unit)
                            check(finishAssembly.await(10, TimeUnit.SECONDS))
                            Any()
                        }, onDiscard = {})
                    }
                })
                entered.await()
                val deletion = async { provider.delete(userId) }
                advanceTimeBy(3_001)
                runCurrent()
                assertFalse(deletion.isCompleted)
                assertFalse(removed)
                assertSame(storage, logic.userStorageProvider.get(userId))
                assertFailsWith<IllegalStateException> { scope.createConsumer() }
                finishAssembly.countDown()
                assertIs<CancellationException>(assembly.get(10, TimeUnit.SECONDS).exceptionOrNull())
                deletion.await()
                assertTrue(removed)
                assertNull(logic.userStorageProvider.get(userId))
            } finally {
                finishAssembly.countDown()
                releaseWorker.complete(Unit)
                thread.shutdownNow()
                withContext(NonCancellable + Dispatchers.Default) {
                    legacyWorker.join()
                    provider.delete(userId)
                }
                assertTrue(thread.awaitTermination(10, TimeUnit.SECONDS))
            }
        }
    }

    @Test
    fun staleOriginCannotPrepareClearStopOrRemoveReopenedGeneration() = runTest {
        val userId = UserId("stale-origin", "example.com")
        withAccounts(userId) { logic, network ->
            val provider = retainingProvider(logic)
            val first = provider.getOrCreate(userId)
            assertTrue(provider.logout(userId, first, prepare = {}, clearLocalData = {}))
            val second = provider.open(userId)
            val storage = assertNotNull(logic.userStorageProvider.get(userId))
            val apis = assertNotNull(network.get(userId.toApi()))
            val worker = second.launch(start = CoroutineStart.UNDISPATCHED) { awaitCancellation() }
            try {
                assertNotSame(first, second)
                assertFalse(provider.logout(userId, first, prepare = { error("Stale preparation") }, clearLocalData = {
                    error("Stale local data removal")
                }))
                assertSame(second, provider.get(userId))
                assertTrue(worker.isActive)
                assertTrue(second.coroutineContext.job.isActive)
                assertSame(storage, logic.userStorageProvider.get(userId))
                assertSame(apis, network.get(userId.toApi()))
            } finally {
                worker.cancel()
            }
        }
    }

    @Test
    fun storeSessionReplacesGenerationAndRejectsOutgoingConsumersBeforeCredentialMutation() = runTest {
        val userId = UserId("relogin", "example.com")
        withAccounts(userId) { logic, _ ->
            val provider = retainingProvider(logic)
            val first = provider.getOrCreate(userId)
            val storing = CompletableDeferred<Unit>()
            val finishStore = CompletableDeferred<Unit>()
            val repository = logic.getGlobalScope().sessionRepository
            val guardedRepository = object : SessionRepository by repository {
                override suspend fun storeSession(session: StoreSessionParam): Either<StorageFailure, Unit> {
                    assertNull(provider.get(userId))
                    assertFalse(first.coroutineContext.job.isActive)
                    assertFailsWith<IllegalStateException> { first.createConsumer() }
                    storing.complete(Unit)
                    finishStore.await()
                    return repository.storeSession(session)
                }
            }
            val replacement = async {
                provider.storeSession(
                    StoreSessionParam(
                        serverConfigId = SERVER_CONFIG_ID,
                        ssoId = null,
                        accountTokens = AccountTokens(userId, "access", "refresh", "Bearer", null),
                        proxyCredentials = null,
                        isPersistentWebSocketEnabled = false,
                    ),
                    guardedRepository,
                )
            }
            try {
                storing.await()
                assertFailsWith<IllegalStateException> { provider.getOrCreate(userId) }
                finishStore.complete(Unit)
                assertIs<Either.Right<Unit>>(replacement.await())
                assertNull(provider.get(userId))
                val second = provider.open(userId)
                assertNotSame(first, second)
                assertSame(second, provider.get(userId))
                assertFalse(provider.logout(userId, first, prepare = { error("Stale preparation") }, clearLocalData = {}))
                second.createConsumer().closeAndJoin()
            } finally {
                finishStore.complete(Unit)
                replacement.join()
            }
        }
    }

    @Test
    fun deleteSessionSerializesMetadataDeletionAgainstReopen() = runTest {
        val userId = UserId("deleting-session", "example.com")
        withAccounts(userId) { logic, _ ->
            val provider = retainingProvider(logic)
            val scope = provider.getOrCreate(userId)
            val deleting = CompletableDeferred<Unit>()
            val finishDelete = CompletableDeferred<Unit>()
            val repository = logic.getGlobalScope().sessionRepository
            val guardedRepository = object : SessionRepository by repository {
                override suspend fun deleteSession(userId: UserId): Either<StorageFailure, Unit> {
                    assertNull(provider.get(userId))
                    assertFalse(scope.coroutineContext.job.isActive)
                    assertFailsWith<IllegalStateException> { scope.createConsumer() }
                    deleting.complete(Unit)
                    finishDelete.await()
                    return repository.deleteSession(userId)
                }
            }
            val deletion = async { provider.deleteSession(userId, guardedRepository) }
            try {
                deleting.await()
                assertFailsWith<IllegalStateException> { provider.getOrCreate(userId) }
                finishDelete.complete(Unit)
                assertIs<Either.Right<Unit>>(deletion.await())
                assertNull(provider.get(userId))
                assertNull(logic.userStorageProvider.get(userId))
            } finally {
                finishDelete.complete(Unit)
                deletion.join()
            }
        }
    }

    @Test
    fun failedProviderRemovalKeepsReopenClosedUntilCleanupSucceeds() = runTest {
        val userId = UserId("failed-removal", "example.com")
        withAccounts(userId) { logic, network ->
            var failRemoval = true
            val provider = commonProvider(logic, removeNetwork = {
                check(!failRemoval) { "Network removal failed" }
                network.remove(it.toApi())
            })
            val scope = provider.getOrCreate(userId)
            val apis = assertNotNull(network.get(userId.toApi()))
            try {
                assertFailsWith<IllegalStateException> { provider.delete(userId) }
                assertNull(provider.get(userId))
                assertFailsWith<IllegalStateException> { provider.getOrCreate(userId) }
                assertSame(apis, network.get(userId.toApi()))
                failRemoval = false
                provider.delete(userId)
                assertNotSame(scope, provider.getOrCreate(userId))
            } finally {
                failRemoval = false
                provider.delete(userId)
            }
        }
    }

    @Test
    fun logoutClosesAdmissionBeforePreparationAndRetainsOutgoingScopeOnFailure() = runTest {
        val userId = UserId("failed-prepare", "example.com")
        withAccounts(userId) { logic, network ->
            val provider = retainingProvider(logic)
            val scope = provider.getOrCreate(userId)
            val storage = logic.userStorageProvider.get(userId)
            val apis = network.get(userId.toApi())
            assertFailsWith<IllegalStateException> {
                provider.logout(userId, scope, prepare = {
                    assertNull(provider.get(userId))
                    assertFailsWith<IllegalStateException> { provider.getOrCreate(userId) }
                    error("Preparation failed")
                }, clearLocalData = { error("Must not clear after failed preparation") })
            }
            assertNull(provider.get(userId))
            assertFailsWith<IllegalStateException> { provider.getOrCreate(userId) }
            assertSame(storage, logic.userStorageProvider.get(userId))
            assertSame(apis, network.get(userId.toApi()))
            // Common cleanup can retry the retained generation; Apple keeps its stricter quarantine.
            assertTrue(provider.logout(userId, scope, prepare = {}, clearLocalData = {}))
        }
    }

    @Test
    fun sqlLogoutPublishesInvalidWhilePrepareWaitsWithoutReattachingOutgoingObserver() = runTest {
        withContext(Dispatchers.Default) {
            val user = UserId("observed-logout", "example.com")
            withAccounts(user) { logic, network ->
                val repository = logic.getGlobalScope().sessionRepository
                assertIs<Either.Right<Unit>>(repository.updateCurrentSession(user))
                val attempts = Channel<Boolean>(Channel.UNLIMITED)
                val provider = commonProvider(logic, startScope = {}, observationAttempt = { attempts.trySend(it != null) },
                    removeNetwork = { network.remove(it.toApi()) })
                val values = Channel<UserSessionScope?>(Channel.UNLIMITED)
                val drained = CompletableDeferred<Unit>()
                val observation = launch {
                    provider.observeValidSession<UserSessionScope>(user, repository) {
                        val captured = this
                        flow {
                            try {
                                emit(captured)
                                awaitCancellation()
                            } finally {
                                // The DB read suspends: cleanup must survive cancellation of its observation.
                                withContext(NonCancellable) {
                                    assertIs<Either.Right<AccountInfo>>(repository.currentSession())
                                    drained.complete(Unit)
                                }
                            }
                        }
                    }.collect { values.send(it) }
                }
                val raw = Channel<CurrentSessionResult>(Channel.UNLIMITED)
                val metadata = launch { CurrentSessionFlowUseCase(repository)().collect { raw.send(it) } }
                val finishPrepare = CompletableDeferred<Unit>()
                var logout: Job? = null
                try {
                    val outgoing = values.nextAvailable()
                    assertTrue(attempts.receive())
                    assertIs<AccountInfo.Valid>(assertIs<CurrentSessionResult.Success>(raw.receive()).accountInfo)
                    logout = launch {
                        assertTrue(provider.logout(user, outgoing, prepare = {
                            assertIs<Either.Right<Unit>>(repository.logout(user, LogoutReason.SESSION_EXPIRED))
                            finishPrepare.await()
                        }, clearLocalData = {}))
                    }
                    val invalid = assertIs<CurrentSessionResult.Success>(raw.receive()).accountInfo
                    assertEquals(AccountInfo.Invalid(user, LogoutReason.SESSION_EXPIRED), invalid)
                    drained.await()
                    assertFalse(logout.isCompleted)
                    assertNull(provider.get(user))
                    // A delayed consumer receiving the already-persisted Invalid/old Valid remains live.
                    val unavailable = async { provider.observeValidSession<Int>(user, repository) { error("Outgoing callback") }.first() }
                    assertNull(unavailable.await())
                    assertTrue(observation.isActive)
                    finishPrepare.complete(Unit)
                    logout.join()
                    assertTrue(observation.isActive)
                } finally {
                    finishPrepare.complete(Unit)
                    logout?.join()
                    observation.cancelAndJoin()
                    metadata.cancelAndJoin()
                }
            }
        }
    }

    @Test
    @Suppress("LongMethod") // Two transitions share one real CurrentAccount subscription to prove equal-Valid suppression.
    fun retainedCurrentReauthWaitsForActualTokenWriteAndSameValidReplacementWakesByRevision() = runTest {
        withContext(Dispatchers.Default) {
            val user = UserId("observed-reauth", "example.com")
            withAccounts(user) { logic, network ->
                val real = logic.getGlobalScope().sessionRepository
                assertIs<Either.Right<Unit>>(real.updateCurrentSession(user))
                assertIs<Either.Right<Unit>>(real.logout(user, LogoutReason.SESSION_EXPIRED))
                val tokens = fixtureTokens(logic)
                val tokenEntered = Channel<Unit>(Channel.UNLIMITED)
                val finishToken = java.util.concurrent.Semaphore(0)
                val blockedTokens = object : AuthTokenStorage by tokens {
                    override fun addOrReplace(token: AuthTokenEntity, proxy: ProxyCredentialsEntity?) {
                        tokenEntered.trySend(Unit)
                        check(finishToken.tryAcquire(10, TimeUnit.SECONDS)) { "Token barrier timed out" }
                        tokens.addOrReplace(token, proxy)
                    }
                }
                val db = fixtureGlobalDatabases.last()
                val repository = SessionDataSource(db.accountsDAO, blockedTokens, db.serverConfigurationDAO)
                val firstValid = CompletableDeferred<Unit>()
                val rejected = Channel<Unit>(Channel.UNLIMITED)
                val provider = commonProvider(logic, startScope = {}, beforeObservation = { firstValid.await() },
                    observationAttempt = { if (it == null) rejected.trySend(Unit) },
                    removeNetwork = { network.remove(it.toApi()) })
                val values = Channel<UserSessionScope?>(Channel.UNLIMITED)
                val attachedTokens = Channel<String?>(Channel.UNLIMITED)
                val observation = launch {
                    provider.observeValidSession<UserSessionScope>(user, repository) {
                        val captured = this
                        attachedTokens.send(tokens.getToken(user.toDao())?.accessToken)
                        flow { emit(captured); awaitCancellation() }
                    }.collect { values.send(it) }
                }
                val raw = Channel<CurrentSessionResult>(Channel.UNLIMITED)
                val metadata = launch { CurrentSessionFlowUseCase(repository)().collect { raw.send(it) } }
                var replacement: Job? = null
                try {
                    assertIs<AccountInfo.Invalid>(assertIs<CurrentSessionResult.Success>(raw.receive()).accountInfo)
                    assertNull(values.receive())
                    replacement = launch { assertIs<Either.Right<Unit>>(provider.storeSession(session(user, "first"), repository)) }
                    tokenEntered.receive()
                    // This Valid is emitted by Accounts.sq's retained CurrentAccount upsert, while
                    // the synchronous AuthTokenStorage write is still blocked on a worker thread.
                    assertEquals(AccountInfo.Valid(user), assertIs<CurrentSessionResult.Success>(raw.receive()).accountInfo)
                    firstValid.complete(Unit)
                    rejected.receive() // Admission processed the early real Valid while token storage remains blocked.
                    assertFalse(replacement.isCompleted)
                    assertTrue(attachedTokens.tryReceive().isFailure)
                    assertNull(tokens.getToken(user.toDao()))
                    finishToken.release()
                    replacement.join()
                    val first = values.nextAvailable()
                    assertEquals("first", attachedTokens.receive())
                    // SQL emits equal Valid metadata. CurrentSessionFlow suppresses it. Reattachment
                    // must therefore be driven by registry revision, not a fabricated metadata value.
                    replacement = launch { assertIs<Either.Right<Unit>>(provider.storeSession(session(user, "second"), repository)) }
                    tokenEntered.receive()
                    assertFalse(replacement.isCompleted)
                    assertTrue(attachedTokens.tryReceive().isFailure)
                    finishToken.release()
                    replacement.join()
                    val second = values.nextAvailable()
                    assertNotSame(first, second)
                    assertEquals("second", attachedTokens.receive())
                    assertTrue(raw.tryReceive().isFailure, "Equal Valid was not suppressed")
                } finally {
                    finishToken.release()
                    replacement?.join()
                    observation.cancelAndJoin()
                    metadata.cancelAndJoin()
                }
            }
        }
    }

    @Test
    fun observerUnwindCanInitializeCellsAndMustDrainBeforeShutdownSnapshotsUnusedState() = runTest {
        withContext(Dispatchers.Default) {
            val user = UserId("observed-lazy-cells", "example.com")
            withAccounts(user) { logic, network ->
                val repository = logic.getGlobalScope().sessionRepository
                val provider = commonProvider(logic, startScope = {}, removeNetwork = { network.remove(it.toApi()) })
                val attached = CompletableDeferred<UserSessionScope>()
                val unwind = CompletableDeferred<Unit>()
                val finishUnwind = CompletableDeferred<Unit>()
                val cellsWorker = CompletableDeferred<Job>()
                val observation = launch {
                    provider.observeValidSession<Int>(user, repository) {
                        attached.complete(this)
                        try {
                            awaitCancellation()
                        } finally {
                            withContext(NonCancellable) {
                                unwind.complete(Unit)
                                finishUnwind.await()
                                // The callback was admitted before close; construction may finish
                                // after cancellation. Shutdown must not have skipped this lazy Cells.
                                cellsWorker.complete(cells.launch(start = CoroutineStart.UNDISPATCHED) { awaitCancellation() })
                            }
                        }
                    }.collect()
                }
                var deletion: Job? = null
                try {
                    val scope = attached.await()
                    assertFalse(scope.isCellsInitialized)
                    deletion = launch { provider.delete(user) }
                    unwind.await()
                    assertFalse(deletion.isCompleted)
                    assertNotNull(logic.userStorageProvider.get(user))
                    assertTrue(scope.coroutineContext.job.isActive, "Legacy shutdown ran before observer unwind")
                    finishUnwind.complete(Unit)
                    deletion.join()
                    assertTrue(cellsWorker.await().isCompleted)
                    assertTrue(scope.cells.coroutineContext.job.isCompleted)
                } finally {
                    finishUnwind.complete(Unit)
                    deletion?.join()
                    observation.cancelAndJoin()
                }
            }
        }
    }

    @Test
    fun callbackFailuresAndCallerCancellationPropagateWithoutBeingUnavailable() = runTest {
        withContext(Dispatchers.Default) {
            val user = UserId("observed-failure", "example.com")
            withAccounts(user) { logic, network ->
                val repository = logic.getGlobalScope().sessionRepository
                val provider = commonProvider(logic, startScope = {}, removeNetwork = { network.remove(it.toApi()) })
                val failure = IllegalStateException("Real callback defect")
                val callbackFailure = runCatching {
                    provider.observeValidSession<Int>(user, repository) { throw failure }.first { it != null }
                }.exceptionOrNull()
                assertTrue(assertIs<IllegalStateException>(callbackFailure).hasCauseIdentity(failure))
                val callbackCancellation = CancellationException("Callback cancelled independently")
                val cancelledCallback = runCatching {
                    provider.observeValidSession<Int>(user, repository) { throw callbackCancellation }.first { it != null }
                }.exceptionOrNull()
                assertTrue(assertIs<CancellationException>(cancelledCallback).hasCauseIdentity(callbackCancellation))
                val entered = CompletableDeferred<Unit>()
                val unwound = CompletableDeferred<Unit>()
                val collector = async {
                    runCatching {
                        provider.observeValidSession<Int>(user, repository) {
                            entered.complete(Unit)
                            try { awaitCancellation() } finally { unwound.complete(Unit) }
                        }.collect()
                    }.exceptionOrNull()
                }
                entered.await()
                collector.cancel(CancellationException("Caller cancelled"))
                collector.join()
                unwound.await()
                assertTrue(collector.isCancelled)
                assertNotNull(provider.get(user), "Caller cancellation must not close the generation")
            }
        }
    }

    @Test
    fun failedCredentialWriteAndProviderRemovalStayQuarantinedWhileQualifiedOtherUserProgresses() = runTest {
        withContext(Dispatchers.Default) {
            val user = UserId("same", "example.com")
            val other = UserId("same", "other.example")
            withAccounts(user, other) { logic, network ->
                val tokens = fixtureTokens(logic)
                val db = fixtureGlobalDatabases.last()
                val tokenFailure = IllegalStateException("Credential write failed")
                val failedTokens = object : AuthTokenStorage by tokens {
                    override fun addOrReplace(token: AuthTokenEntity, proxy: ProxyCredentialsEntity?) { throw tokenFailure }
                }
                val repository = SessionDataSource(db.accountsDAO, failedTokens, db.serverConfigurationDAO)
                var failRemoval = false
                val provider = commonProvider(logic, startScope = {}, removeNetwork = {
                    if (failRemoval && it == user) error("Removal failed")
                    network.remove(it.toApi())
                })
                val result = provider.storeSession(session(user, "partial"), repository)
                assertSame(tokenFailure, assertIs<StorageFailure.Generic>(assertIs<Either.Left<StorageFailure>>(result).value).rootCause)
                assertEquals(AccountInfo.Valid(user), assertIs<Either.Right<AccountInfo>>(repository.userAccountInfo(user)).value)
                assertNull(provider.observeValidSession<Int>(user, repository) { error("Quarantined token owner") }.first())
                assertNull(provider.get(user))
                // A separately qualified user must not wait for A's failed mutation or unavailable observation.
                val otherScope = provider.observeValidSession(other, repository) { flowOf(this) }.first { it != null }
                assertEquals(other, assertNotNull(otherScope).userId)
                provider.delete(user) // explicit supported cleanup; metadata remains Valid
                val first = provider.getOrCreate(user)
                try {
                    failRemoval = true
                    assertFailsWith<IllegalStateException> { provider.delete(user) }
                    assertNull(provider.get(user))
                    assertNull(provider.observeValidSession<Int>(user, repository) { error("Quarantined removal owner") }.first())
                    assertEquals(first.userId, user)
                } finally {
                    failRemoval = false
                    provider.delete(user)
                }
            }
        }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun bufferedValidIsRecheckedAfterWaitAndLatestSwitchOrCallerCancellationCannotConstructA() = runTest {
        withContext(Dispatchers.Default) {
            val user = UserId("observed-wait", "example.com")
            val other = UserId("observed-wait", "other.example")
            withAccounts(user, other) { logic, network ->
                val repository = logic.getGlobalScope().sessionRepository
                val snapshot = assertIs<Either.Right<List<AccountInfo>>>(repository.allSessions()).value
                // Deliberately freeze the OLD metadata snapshot to model delayed flowOn/shareIn delivery.
                // Live validity still comes from the actual DAO after the barrier.
                val delayed = object : SessionRepository by repository {
                    override fun allSessionsFlow() = flowOf(snapshot)
                }
                var gate = CompletableDeferred<Unit>()
                val waiting = Channel<Unit>(Channel.UNLIMITED)
                val attempted = Channel<Boolean>(Channel.UNLIMITED)
                val constructed = ConcurrentLinkedQueue<UserId>()
                val provider = commonProvider(logic, startScope = {}, createScope = {
                    constructed.add(it)
                    (logic.userSessionScopeProvider.value as UserSessionScopeProviderCommon).create(it)
                }, beforeObservation = {
                    if (it == user) { waiting.send(Unit); gate.await() }
                }, observationAttempt = { attempted.trySend(it != null) }, removeNetwork = { network.remove(it.toApi()) })
                val stale = launch { provider.observeValidSession<Int>(user, delayed) { error("Stale Valid callback") }.collect() }
                try {
                    waiting.receive()
                    assertIs<Either.Right<Unit>>(repository.logout(user, LogoutReason.SESSION_EXPIRED))
                    gate.complete(Unit)
                    assertFalse(attempted.receive())
                    assertTrue(constructed.isEmpty(), "Pre-wait Valid reopened an invalid account")
                } finally { stale.cancelAndJoin() }

                assertIs<Either.Right<Unit>>(repository.storeSession(session(user, "valid-again")))
                gate = CompletableDeferred()
                val selected = MutableStateFlow(user)
                val ready = Channel<UserId>(Channel.UNLIMITED)
                val switched = launch {
                    selected.flatMapLatest { id ->
                        provider.observeValidSession(id, delayed) { flowOf(this.userId) }
                    }.collect { if (it != null) ready.send(it) }
                }
                try {
                    waiting.receive()
                    selected.value = other
                    assertEquals(other, ready.receive())
                    gate.complete(Unit)
                    assertFalse(constructed.contains(user), "Cancelled A attachment resumed after switch to B")
                } finally { switched.cancelAndJoin() }

                gate = CompletableDeferred()
                val cancelled = launch { provider.observeValidSession<Int>(user, delayed) { error("Cancelled callback") }.collect() }
                waiting.receive()
                cancelled.cancelAndJoin()
                gate.complete(Unit)
                assertTrue(cancelled.isCancelled)
                assertFalse(constructed.contains(user))
            }
        }
    }

    @Test
    fun resultWaitingAtDispatcherBoundaryCannotEscapeAfterRevocation() = runTest {
        withContext(Dispatchers.Default) {
            val user = UserId("observed-delivery", "example.com")
            withAccounts(user) { logic, network ->
                val repository = logic.getGlobalScope().sessionRepository
                val provider = commonProvider(logic, startScope = {}, removeNetwork = { network.remove(it.toApi()) })
                val attached = CompletableDeferred<UserSessionScope>()
                val subscriberPaused = CompletableDeferred<Unit>()
                val resumeSubscriber = CompletableDeferred<Unit>()
                val unavailableAgain = CompletableDeferred<Unit>()
                val delivered = ConcurrentLinkedQueue<Int>()
                var firstNull = true
                val collector = launch {
                    provider.observeValidSession(user, repository) {
                        attached.complete(this)
                        flowOf(7)
                    }.collect { value ->
                        if (value == null && firstNull) {
                            firstNull = false
                            subscriberPaused.complete(Unit)
                            resumeSubscriber.await()
                        } else if (value == null) unavailableAgain.complete(Unit)
                        else delivered.add(value)
                    }
                }
                try {
                    subscriberPaused.await()
                    val outgoing = attached.await()
                    // The initial unavailable value has reached the subscriber. Its paused handler
                    // keeps the outgoing result pending across the provider's dispatcher boundary.
                    assertTrue(provider.logout(user, outgoing, prepare = {
                        assertIs<Either.Right<Unit>>(repository.logout(user, LogoutReason.SESSION_EXPIRED))
                    }, clearLocalData = {}))
                    resumeSubscriber.complete(Unit)
                    unavailableAgain.await()
                    assertTrue(delivered.isEmpty(), "Outgoing result crossed revoked admission")
                    assertTrue(collector.isActive)
                } finally {
                    resumeSubscriber.complete(Unit)
                    collector.cancelAndJoin()
                }
            }
        }
    }

    @Test
    fun failedStartupOwnerCannotBeReopenedByObservationAndOriginalFailureIsPreserved() = runTest {
        withContext(Dispatchers.Default) {
            val user = UserId("observed-startup-failure", "example.com")
            withAccounts(user) { logic, network ->
                val repository = logic.getGlobalScope().sessionRepository
                val failure = IllegalStateException("Startup failed")
                var starts = 0
                val attempts = Channel<Boolean>(Channel.UNLIMITED)
                val provider = commonProvider(logic, startScope = { starts++; throw failure },
                    observationAttempt = { attempts.trySend(it != null) }, removeNetwork = { network.remove(it.toApi()) })
                assertSame(failure, runCatching { provider.getOrCreate(user) }.exceptionOrNull())
                val observer = launch {
                    provider.observeValidSession<Int>(user, repository) { error("Failed owner became ready") }.collect()
                }
                try {
                    assertFalse(attempts.receive())
                    assertNull(provider.get(user))
                    assertEquals(1, starts)
                    assertTrue(observer.isActive, "Quarantine terminated the observer instead of remaining unavailable")
                } finally { observer.cancelAndJoin() }
            }
        }
    }

    @Test
    fun explicitDeleteLeavesSubscribedObserversIdleUntilManualOpenOrSuccessfulStore() = runTest {
        withContext(Dispatchers.Default) {
            val user = UserId("observed-explicit-release", "example.com")
            withAccounts(user) { logic, network ->
                val repository = logic.getGlobalScope().sessionRepository
                assertIs<Either.Right<Unit>>(repository.updateCurrentSession(user))
                var starts = 0
                val provider = commonProvider(logic, startScope = { starts++ }, removeNetwork = { network.remove(it.toApi()) })
                // Read the existing gate, without adding a production test accessor or resolving a lazy resource.
                val field = UserSessionScopeProviderCommon::class.java.getDeclaredField("sessions").also { it.isAccessible = true }
                @Suppress("UNCHECKED_CAST")
                val account = (field.get(provider) as UserSessionRegistry<UserSessionScope>).account(user)
                val values = Channel<UserSessionScope?>(Channel.UNLIMITED)
                val observer = launch {
                    provider.observeValidSession<UserSessionScope>(user, repository) {
                        val captured = this
                        flow { emit(captured); awaitCancellation() }
                    }.collect { values.send(it) }
                }
                try {
                    val first = values.nextAvailable() // A fresh CoreLogic can open its first valid observed owner.
                    assertEquals(1, starts)
                    provider.delete(user)
                    assertFalse(account.isAvailable(account.revision.value), "Release re-enabled observer admission")
                    assertNull(provider.get(user))
                    assertEquals(AccountInfo.Valid(user), assertIs<Either.Right<AccountInfo>>(repository.currentSession()).value)
                    assertEquals(1, starts)
                    assertTrue(observer.isActive)
                    val second = provider.open(user) // Explicit host open, not a new metadata value.
                    assertSame(second, values.nextAvailable())
                    assertNotSame(first, second)
                    assertEquals(2, starts)
                    provider.delete(user)
                    assertFalse(account.isAvailable(account.revision.value))
                    assertEquals(2, starts)
                    assertIs<Either.Right<Unit>>(provider.storeSession(session(user, "after-release"), repository))
                    val third = values.nextAvailable() // Equal Valid metadata; store release must wake the subscriber.
                    assertNotSame(second, third)
                    assertEquals(3, starts)
                    assertTrue(observer.isActive)
                } finally { observer.cancelAndJoin() }
            }
        }
    }

    @Test
    fun ownedObserverCancellationAcrossIoRemainsUnavailableWithoutKillingSubscriber() = runTest {
        withContext(Dispatchers.Default) {
            val user = UserId("observed-recovered-revocation", "example.com")
            withAccounts(user) { logic, network ->
                val repository = logic.getGlobalScope().sessionRepository
                val admissions = Channel<Observation<UserSessionScope>>(Channel.UNLIMITED)
                val provider = commonProvider(logic, startScope = {}, observationAttempt = {
                    if (it != null) admissions.trySend(it)
                }, removeNetwork = { network.remove(it.toApi()) })
                val values = Channel<UserSessionScope?>(Channel.UNLIMITED)
                val observer = launch {
                    provider.observeValidSession<UserSessionScope>(user, repository) {
                        val captured = this
                        flow { emit(captured); awaitCancellation() }
                    }.collect { values.send(it) }
                }
                try {
                    val first = values.nextAvailable()
                    val admitted = admissions.receive()
                    // Do not advance registry revision: collectLatest must remain active so the
                    // withContext/IO cancellation reaches the provider's own-revocation handler.
                    admitted.work.close()
                    admitted.work.awaitClosed()
                    assertNull(values.receive())
                    assertTrue(observer.isActive)
                    assertIs<Either.Right<Unit>>(provider.storeSession(session(user, "recovered"), repository))
                    assertNotSame(first, values.nextAvailable())
                    assertTrue(observer.isActive)
                } finally { observer.cancelAndJoin() }
            }
        }
    }

    private suspend fun <T : Any> Channel<T?>.nextAvailable(): T {
        while (true) receive()?.let { return it }
    }

    private fun session(user: UserId, token: String) = StoreSessionParam(
        serverConfigId = SERVER_CONFIG_ID,
        ssoId = null,
        accountTokens = AccountTokens(user, token, "refresh-$token", "Bearer", null),
        proxyCredentials = null,
        isPersistentWebSocketEnabled = false,
    )

    private fun fixtureTokens(logic: CoreLogic): AuthTokenStorage {
        val getter = CoreLogic::class.java.getDeclaredMethod("getGlobalPreferences")
        getter.isAccessible = true
        return (getter.invoke(logic) as GlobalPrefProvider).authTokenStorage
    }

    private suspend fun withAccounts(
        vararg users: UserId,
        block: suspend (CoreLogic, PlatformUserAuthenticatedNetworkProvider) -> Unit,
    ) {
        val root = Files.createTempDirectory("session-registry").toFile()
        users.forEach { storeAccount(root, it) }
        val network = PlatformUserAuthenticatedNetworkProvider()
        val logic = CoreLogic(
            rootPath = root.absolutePath,
            kaliumConfigs = KaliumConfigs(enableCalling = false, mockedRequests = emptyList()),
            userAgent = "kalium-registry-test",
            userAuthenticatedNetworkProvider = network,
        ).also { retainGlobalDatabase(it) }
        try {
            block(logic, network)
        } finally {
            closeFixture(root, logic, *users)
        }
    }

    private fun commonProvider(
        logic: CoreLogic,
        createScope: (UserId) -> UserSessionScope = { (logic.userSessionScopeProvider.value as UserSessionScopeProviderCommon).create(it) },
        startScope: (UserSessionScope) -> Unit = { it.startRuntime() },
        beforeObservation: suspend (UserId) -> Unit = {},
        observationAttempt: (Observation<UserSessionScope>?) -> Unit = {},
        removeNetwork: suspend (UserId) -> Unit,
    ): UserSessionScopeProviderCommon = object : UserSessionScopeProviderCommon(
        logic.globalCallManager,
        logic.userStorageProvider,
        removeNetwork,
        "kalium-registry-test",
    ) {
        override fun create(userId: UserId): UserSessionScope = createScope(userId).also { retainScope(logic, it) }
        override fun startRuntime(scope: UserSessionScope) = startScope(scope)
        override suspend fun admitObservation(
            userId: UserId,
            sessionRepository: SessionRepository,
            revision: Long,
        ): Observation<UserSessionScope>? {
            beforeObservation(userId)
            return super.admitObservation(userId, sessionRepository, revision).also(observationAttempt)
        }
    }.also {
        fixtureProviders.add(it)
        fixtureGlobalJobs.add(logic.getGlobalScope().coroutineContext.job)
    }

    private fun retainingProvider(logic: CoreLogic): UserSessionScopeProvider {
        val delegate = logic.userSessionScopeProvider.value
        fixtureGlobalJobs.add(logic.getGlobalScope().coroutineContext.job)
        return object : UserSessionScopeProvider by delegate {
            override fun getOrCreate(userId: UserId): UserSessionScope =
                delegate.getOrCreate(userId).also { retainScope(logic, it) }

            override suspend fun open(userId: UserId): UserSessionScope =
                delegate.open(userId).also { retainScope(logic, it) }

            override fun <T> getOrCreate(userId: UserId, action: UserSessionScope.() -> T): T = getOrCreate(userId).action()

            override suspend fun <T> open(userId: UserId, action: UserSessionScope.() -> T): T = open(userId).action()
        }.also { fixtureProviders.add(it) }
    }

    private fun retainScope(logic: CoreLogic, scope: UserSessionScope) {
        // userStorage is an eager retained val; capture its actual builder before cache eviction.
        fixtureScopes.add(scope)
        fixtureDatabases.add(scope.userStorage.database)
        fixtureGlobalJobs.add(logic.getGlobalScope().coroutineContext.job)
    }

    private fun retainGlobalDatabase(logic: CoreLogic) {
        // CoreLogic initializes this database eagerly. Reflection reads its protected getter; it
        // neither initializes a lazy resource nor changes the SDK's visibility for this test.
        val getter = CoreLogic::class.java.getDeclaredMethod("getGlobalDatabaseBuilder")
        getter.isAccessible = true
        fixtureGlobalDatabases.add(getter.invoke(logic) as GlobalDatabaseBuilder)
    }

    private suspend fun closeFixture(root: File, logic: CoreLogic, vararg users: UserId) {
        // Only teardown uses the real clock. Tests explicitly advancing the legacy timeout retain
        // their virtual-time operations and every existing assertion.
        withContext(NonCancellable + Dispatchers.Default) {
            withTimeout(30_000) {
                val globalJobs = fixtureGlobalJobs.toList().distinct()
                globalJobs.forEach { it.cancel() }
                globalJobs.forEach { it.join() }
                val defaultProvider = logic.userSessionScopeProvider.takeIf { it.isInitialized() }?.value
                val providers = fixtureProviders.toList() + listOfNotNull(defaultProvider)
                users.forEach { user -> providers.forEach { it.delete(user) } }
                val scopes = fixtureScopes.toList().distinct()
                val detachedJobs = scopes.flatMap {
                    listOfNotNull(initializedKeyPackageJob(it), initializedLegalHoldJob(it))
                }
                detachedJobs.forEach { it.cancel() }
                detachedJobs.forEach { it.join() }
                scopes.forEach { it.coroutineContext.job.cancel() }
                scopes.forEach { it.coroutineContext.job.join() }
                // A prior best-effort timeout can leave later shutdown steps unvisited. Re-run
                // shutdown on the retained owner after its session job has actually finished.
                scopes.forEach { it.stopAccountWork() }
                scopes.forEach {
                    assertTrue(it.coroutineContext.job.isCompleted, "Account work still owns fixture storage: ${it.userId}")
                    assertTrue(it.coroutineContext.job.children.none(), "Account retains child jobs: ${it.userId}")
                    if (it.isCellsInitialized) {
                        assertTrue(it.cells.coroutineContext.job.isCompleted, "Cells work still owns fixture storage: ${it.userId}")
                    }
                }
                globalJobs.forEach {
                    assertTrue(it.isCompleted, "Global work still owns fixture storage")
                    assertTrue(it.children.none(), "Global owner retains child jobs")
                }
                // remove(userId) only evicts the cache; it does not close the evicted JDBC driver.
                fixtureDatabases.toList().distinct().forEach { it.close() }
                fixtureGlobalDatabases.toList().distinct().forEach { it.close() }
                assertTrue(root.deleteRecursively(), "Unable to remove drained fixture storage: $root")
            }
        }
    }

    private fun initializedKeyPackageJob(scope: UserSessionScope): Job? {
        // This baseline worker has its own scope. Read only the initialized delegate: teardown
        // must not start an unused worker, or keep it alive after the fixture database closes.
        val delegateField = UserSessionScope::class.java.getDeclaredField("keyPackageManager\$delegate")
        delegateField.isAccessible = true
        val delegate = delegateField.get(scope) as Lazy<*>
        if (!delegate.isInitialized()) return null
        val manager = checkNotNull(delegate.value)
        val scopeField = manager.javaClass.getDeclaredField("refillKeyPackagesScope")
        scopeField.isAccessible = true
        return (scopeField.get(manager) as CoroutineScope).coroutineContext.job
    }

    private fun initializedLegalHoldJob(scope: UserSessionScope): Job? {
        val delegateField = UserSessionScope::class.java.getDeclaredField("legalHoldHandler\$delegate")
        delegateField.isAccessible = true
        val delegate = delegateField.get(scope) as Lazy<*>
        if (!delegate.isInitialized()) return null
        val handler = checkNotNull(delegate.value)
        val scopeField = handler.javaClass.getDeclaredField("scope")
        scopeField.isAccessible = true
        return (scopeField.get(handler) as CoroutineScope).coroutineContext.job
    }

    private suspend fun storeAccount(root: File, userId: UserId) {
        val database = globalDatabaseProvider(
            platformDatabaseData = PlatformDatabaseData(StorageData.FileBacked(File(root, "global-storage"))),
            queriesContext = KaliumDispatcherImpl.io,
            passphrase = null,
        )
        try {
            if (database.serverConfigurationDAO.configById(SERVER_CONFIG_ID) == null) database.serverConfigurationDAO.insert(
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
    }

    private companion object {
        const val SERVER_CONFIG_ID = "test-server"
        const val URL = "https://example.invalid"
    }
}
