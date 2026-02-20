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

package com.wire.kalium.persistence.daokaliumdb

import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.GlobalDatabaseBuilder
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.globalDatabaseProvider
import com.wire.kalium.persistence.model.ServerConfigEntity
import com.wire.kalium.persistence.utils.stubs.newServerConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ServerConfigurationDAONativePushJvmTest {

    private val config = newServerConfig(id = 1)

    private lateinit var databaseFile: File
    private lateinit var globalDatabaseBuilder: GlobalDatabaseBuilder

    @BeforeTest
    fun setup() {
        databaseFile = Files.createTempDirectory("test-storage").toFile().resolve("test-kalium.db")
    }

    @AfterTest
    fun tearDown() {
        if (::globalDatabaseBuilder.isInitialized) {
            globalDatabaseBuilder.nuke()
        } else {
            databaseFile.delete()
        }
    }

    @Test
    fun givenUserWithConfiguredServer_whenReadingNativePush_thenDefaultValueIsTrue() = runTest {
        globalDatabaseBuilder = createDatabase(testScheduler)
        val userId = UserIDEntity("user", "wire.com")
        insertConfig(config)
        insertAccount(userId, config.id)

        val result = globalDatabaseBuilder.serverConfigurationDAO.nativePushEnabledByUser(userId)

        assertEquals(true, result)
    }

    @Test
    fun givenUserWithConfiguredServer_whenUpdatingNativePush_thenStoredValueChanges() = runTest {
        globalDatabaseBuilder = createDatabase(testScheduler)
        val userId = UserIDEntity("user", "wire.com")
        insertConfig(config)
        insertAccount(userId, config.id)

        globalDatabaseBuilder.serverConfigurationDAO.updateNativePushEnabledByUser(userId, false)
        val result = globalDatabaseBuilder.serverConfigurationDAO.nativePushEnabledByUser(userId)

        assertEquals(false, result)
    }

    private fun createDatabase(testScheduler: TestCoroutineScheduler): GlobalDatabaseBuilder = globalDatabaseProvider(
        platformDatabaseData = PlatformDatabaseData(
            StorageData.FileBacked(databaseFile)
        ),
        queriesContext = StandardTestDispatcher(testScheduler),
        passphrase = null,
        enableWAL = false
    )

    private suspend fun insertAccount(userId: UserIDEntity, serverConfigId: String) {
        globalDatabaseBuilder.accountsDAO.insertOrReplace(
            userIDEntity = userId,
            ssoIdEntity = null,
            managedByEntity = null,
            serverConfigId = serverConfigId,
            isPersistentWebSocketEnabled = false
        )
    }

    private suspend fun insertConfig(serverConfigEntity: ServerConfigEntity) {
        with(serverConfigEntity) {
            globalDatabaseBuilder.serverConfigurationDAO.insert(
                ServerConfigurationDAO.InsertData(
                    id = id,
                    apiBaseUrl = links.api,
                    accountBaseUrl = links.accounts,
                    webSocketBaseUrl = links.webSocket,
                    blackListUrl = links.blackList,
                    teamsUrl = links.teams,
                    websiteUrl = links.website,
                    title = links.title,
                    federation = metaData.federation,
                    domain = metaData.domain,
                    commonApiVersion = metaData.apiVersion,
                    isOnPremises = false,
                    apiProxyHost = links.apiProxy?.host,
                    apiProxyNeedsAuthentication = links.apiProxy?.needsAuthentication,
                    apiProxyPort = links.apiProxy?.port
                )
            )
        }
    }
}
