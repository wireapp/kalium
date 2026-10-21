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
        removeNetwork: suspend (UserId) -> Unit,
    ): UserSessionScopeProviderCommon = object : UserSessionScopeProviderCommon(
        logic.globalCallManager,
        logic.userStorageProvider,
        removeNetwork,
        "kalium-registry-test",
    ) {
        override fun create(userId: UserId): UserSessionScope = createScope(userId).also { retainScope(logic, it) }
        override fun startRuntime(scope: UserSessionScope) = startScope(scope)
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
