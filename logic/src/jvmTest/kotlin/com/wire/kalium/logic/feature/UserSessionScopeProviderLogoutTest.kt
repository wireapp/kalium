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
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.globalDatabaseProvider
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserSessionScopeProviderLogoutTest {

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

    private suspend fun storeAccount(root: File, userId: UserId) {
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
    }

    private companion object {
        const val SERVER_CONFIG_ID = "test-server"
        const val URL = "https://example.invalid"
    }
}
