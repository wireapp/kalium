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

import com.wire.kalium.logic.CoreLogic
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserSessionScopeProviderLogoutTest {

    // Retain evicted owners until their jobs have completed and their database can be closed.
    private val fixtureScopes = ConcurrentLinkedQueue<UserSessionScope>()
    private val fixtureDatabases = ConcurrentLinkedQueue<UserDatabaseBuilder>()
    private val fixtureGlobalDatabases = ConcurrentLinkedQueue<GlobalDatabaseBuilder>()
    private val fixtureGlobalJobs = ConcurrentLinkedQueue<Job>()
    private val fixtureProviders = mutableListOf<UserSessionScopeProvider>()

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
    fun givenInitializedCells_whenLoggingOut_thenCellsWorkDrainsBeforeLocalDataIsCleared() = runTest {
        val userId = UserId("user", "example.com")
        withAccounts(userId) { logic, _ ->
            val provider = retainingProvider(logic)
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
                worker.cancelAndJoin()
            }
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
