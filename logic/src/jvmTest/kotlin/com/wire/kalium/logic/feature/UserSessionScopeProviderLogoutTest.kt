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
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.auth.AccountTokens
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.session.StoreSessionParam
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAO
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.globalDatabaseProvider
import com.wire.kalium.usernetwork.di.PlatformUserAuthenticatedNetworkProvider
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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

    @Test
    fun consumerAssemblyStillDrainsBeforeProviderRemovalAfterTheLegacyTimeout() = runTest {
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
            val finish = CountDownLatch(1)
            val thread = Executors.newSingleThreadExecutor()
            try {
                val assembly = thread.submit(Callable {
                    runCatching {
                        consumer.assemble(
                            create = {
                                entered.complete(Unit)
                                check(finish.await(30, TimeUnit.SECONDS))
                                Any()
                            },
                            onDiscard = {},
                        )
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
                finish.countDown()
                assertIs<kotlinx.coroutines.CancellationException>(assembly.get(30, TimeUnit.SECONDS).exceptionOrNull())
                deletion.await()
                assertTrue(removed)
                assertNull(logic.userStorageProvider.get(userId))
            } finally {
                finish.countDown()
                thread.shutdownNow()
                provider.delete(userId)
            }
        }
    }

    @Test
    fun staleOriginCannotPrepareClearStopOrRemoveReopenedGeneration() = runTest {
        val userId = UserId("stale-origin", "example.com")
        withAccounts(userId) { logic, network ->
            val provider = logic.userSessionScopeProvider.value
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
                second.createConsumer().closeAndJoin()
            } finally {
                worker.cancel()
            }
        }
    }

    @Test
    fun concurrentFirstResolutionReturnsOneScope() = runTest {
        val userId = UserId("concurrent-first", "example.com")
        withAccounts(userId) { logic, _ ->
            val provider = logic.userSessionScopeProvider.value
            val threads = Executors.newFixedThreadPool(8)
            try {
                val start = CyclicBarrier(8)
                val scopes = (1..8).map {
                    threads.submit(Callable {
                        start.await(10, TimeUnit.SECONDS)
                        provider.getOrCreate(userId)
                    })
                }.map { it.get(30, TimeUnit.SECONDS) }
                scopes.forEach { assertSame(scopes.first(), it) }
                assertSame(scopes.first(), provider.get(userId))
            } finally {
                threads.shutdownNow()
            }
        }
    }

    @Test
    fun invalidationDuringCreationRejectsPublicationAndWaitsBeforeRemovingResources() = runTest {
        val userId = UserId("creating", "example.com")
        val otherUser = UserId(userId.value, "other.example")
        withAccounts(userId, otherUser) { logic, network ->
            val constructed = CompletableDeferred<UserSessionScope>()
            val finishCreation = CountDownLatch(1)
            val removed = CompletableDeferred<Unit>()
            val delegate = logic.userSessionScopeProvider.value as UserSessionScopeProviderCommon
            val provider = commonProvider(logic, createScope = { id ->
                delegate.create(id).also { scope ->
                    if (id == userId) {
                        constructed.complete(scope)
                        check(finishCreation.await(30, TimeUnit.SECONDS))
                    }
                }
            }, removeNetwork = {
                network.remove(it.toApi())
                if (it == userId) removed.complete(Unit)
            })
            val thread = Executors.newSingleThreadExecutor()
            try {
                val creation = thread.submit(Callable { runCatching { provider.getOrCreate(userId) } })
                val outgoing = constructed.await()
                val deletion = async(start = CoroutineStart.UNDISPATCHED) { provider.delete(userId) }
                assertNull(provider.get(userId))
                assertFalse(deletion.isCompleted)
                assertFalse(removed.isCompleted)
                assertFailsWith<IllegalStateException> { provider.getOrCreate(userId) }
                // An account's constructor must not hold the registry's map lock across other accounts.
                val other = provider.getOrCreate(otherUser)
                assertSame(other, provider.get(otherUser))
                assertTrue(other.coroutineContext.job.isActive)
                finishCreation.countDown()
                assertIs<IllegalStateException>(creation.get(30, TimeUnit.SECONDS).exceptionOrNull())
                deletion.await()
                assertTrue(removed.isCompleted)
                assertFalse(outgoing.coroutineContext.job.isActive)
                assertFailsWith<IllegalStateException> { outgoing.createConsumer() }
                assertNull(provider.get(userId))
                assertNull(logic.userStorageProvider.get(userId))
                assertNull(network.get(userId.toApi()))
            } finally {
                finishCreation.countDown()
                thread.shutdownNow()
                provider.delete(userId)
                provider.delete(otherUser)
            }
        }
    }

    @Test
    fun removalBlocksReopenAndConsumerAdmissionWhileOtherQualifiedUsersProgress() = runTest {
        val userId = UserId("same-local-id", "first.example")
        val otherUser = UserId(userId.value, "second.example")
        withAccounts(userId, otherUser) { logic, network ->
            val removing = CompletableDeferred<Unit>()
            val finishRemoval = CompletableDeferred<Unit>()
            val provider = commonProvider(logic, removeNetwork = {
                if (it == userId) {
                    // Storage precedes networking in the established removal order.
                    assertNull(logic.userStorageProvider.get(it))
                    removing.complete(Unit)
                    finishRemoval.await()
                }
                network.remove(it.toApi())
            })
            val outgoing = provider.getOrCreate(userId)
            val deletion = async { provider.delete(userId) }
            try {
                removing.await()
                assertNull(provider.get(userId))
                assertFailsWith<IllegalStateException> { provider.getOrCreate(userId) }
                assertFailsWith<IllegalStateException> { outgoing.createConsumer() }
                val other = provider.open(otherUser)
                assertEquals(otherUser, other.userId)
                assertTrue(provider.logout(otherUser, other, prepare = {}, clearLocalData = {}))
                assertFalse(deletion.isCompleted)
                finishRemoval.complete(Unit)
                deletion.await()
                val reopened = provider.getOrCreate(userId)
                assertNotSame(outgoing, reopened)
                assertSame(reopened, provider.get(userId))
                assertNotNull(logic.userStorageProvider.get(userId))
                assertNotNull(network.get(userId.toApi()))
            } finally {
                finishRemoval.complete(Unit)
                deletion.join()
                provider.delete(userId)
                provider.delete(otherUser)
            }
        }
    }

    @Test
    fun storeSessionReplacesGenerationAndRejectsOutgoingConsumersBeforeCredentialMutation() = runTest {
        val userId = UserId("relogin", "example.com")
        withAccounts(userId) { logic, _ ->
            val provider = logic.userSessionScopeProvider.value
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
            val provider = logic.userSessionScopeProvider.value
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
                assertFailsWith<IllegalStateException> { scope.createConsumer() }
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
            val provider = logic.userSessionScopeProvider.value
            val scope = provider.getOrCreate(userId)
            val storage = logic.userStorageProvider.get(userId)
            val apis = network.get(userId.toApi())
            assertFailsWith<IllegalStateException> {
                provider.logout(userId, scope, prepare = {
                    assertNull(provider.get(userId))
                    assertFailsWith<IllegalStateException> { provider.getOrCreate(userId) }
                    assertFailsWith<IllegalStateException> { scope.createConsumer() }
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
    fun givenAccountWorkDoesNotStop_whenLoggingOut_thenLocalDataIsStillCleared() = runTest {
        val root = Files.createTempDirectory("logout-drain").toFile()
        val userId = UserId("user", "example.com")
        storeAccount(root, userId)
        val logic = CoreLogic(
            rootPath = root.absolutePath,
            kaliumConfigs = KaliumConfigs(enableCalling = false, mockedRequests = emptyList()),
            userAgent = "kalium-logout-test",
        )
        val provider = logic.userSessionScopeProvider.value
        val scope = provider.getOrCreate(userId)
        val release = CompletableDeferred<Unit>()
        val stuckWorker = scope.launch { withContext(NonCancellable) { release.await() } }
        var cleared = false
        try {
            assertTrue(provider.logout(userId, scope, prepare = {}, clearLocalData = { cleared = true }))
            assertTrue(cleared)
            assertFalse(scope.isCellsInitialized)
            assertNull(provider.get(userId))
        } finally {
            release.complete(Unit)
            stuckWorker.join()
            root.deleteRecursively()
        }
    }

    @Test
    fun givenInitializedCells_whenLoggingOut_thenCellsWorkDrainsBeforeLocalDataIsCleared() = runTest {
        val root = Files.createTempDirectory("logout-cells").toFile()
        val userId = UserId("user", "example.com")
        storeAccount(root, userId)
        val logic = CoreLogic(
            rootPath = root.absolutePath,
            kaliumConfigs = KaliumConfigs(enableCalling = false, mockedRequests = emptyList()),
            userAgent = "kalium-logout-test",
        )
        val provider = logic.userSessionScopeProvider.value
        val scope = provider.getOrCreate(userId)
        val cells = scope.cells
        val worker = cells.launch(start = CoroutineStart.UNDISPATCHED) { awaitCancellation() }
        try {
            assertTrue(scope.isCellsInitialized)
            withContext(Dispatchers.Default) {
                assertTrue(provider.logout(userId, scope, prepare = {}, clearLocalData = {
                    assertTrue(worker.isCompleted)
                    assertTrue(cells.coroutineContext.job.isCompleted)
                }))
            }
            assertNull(provider.get(userId))
        } finally {
            worker.cancel()
            logic.getGlobalScope().coroutineContext.job.cancel()
            root.deleteRecursively()
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
        )
        try {
            block(logic, network)
        } finally {
            users.forEach { logic.userSessionScopeProvider.value.delete(it) }
            logic.getGlobalScope().coroutineContext.job.cancel()
            root.deleteRecursively()
        }
    }

    private fun commonProvider(
        logic: CoreLogic,
        createScope: (UserId) -> UserSessionScope = { (logic.userSessionScopeProvider.value as UserSessionScopeProviderCommon).create(it) },
        removeNetwork: suspend (UserId) -> Unit,
    ): UserSessionScopeProviderCommon = object : UserSessionScopeProviderCommon(
        logic.globalCallManager,
        logic.userStorageProvider,
        removeNetwork,
        "kalium-registry-test",
    ) {
        override fun create(userId: UserId): UserSessionScope = createScope(userId)
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
