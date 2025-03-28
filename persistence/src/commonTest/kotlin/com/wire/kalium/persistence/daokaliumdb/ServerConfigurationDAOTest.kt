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

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.wire.kalium.persistence.daokaliumdb

import com.wire.kalium.persistence.GlobalDBBaseTest
import com.wire.kalium.persistence.db.GlobalDatabaseBuilder
import com.wire.kalium.persistence.model.ServerConfigEntity
import com.wire.kalium.persistence.utils.stubs.newServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class ServerConfigurationDAOTest : GlobalDBBaseTest() {

    private val config1 = newServerConfig(id = 1)
    private val config2 = newServerConfig(id = 2)
    private val config3 = newServerConfig(id = 3)

    lateinit var globalDatabaseBuilder: GlobalDatabaseBuilder

    @BeforeTest
    fun setup() {
        globalDatabaseBuilder = createDatabase()
    }

    @AfterTest
    fun nuke() {
        deleteDatabase()
    }

    @Test
    fun givenServerConfig_ThenItCanBeInsertedAndRetrieved() = runTest {
        val expect = config1
        insertConfig(expect)
        val actual = globalDatabaseBuilder.serverConfigurationDAO.configById(expect.id)

        assertEquals(expect, actual)
    }

    @Test
    fun givenAlreadyStoredServerConfig_whenInsertingNewOneWithTheSameApiBaseUrl_thenNothingChanges() = runTest {
        val newLinks = config1.links.copy(api = "new_base_url.com")
        val duplicatedConfig = config1.copy(links = newLinks)
        insertConfig(config1)
        assertFails {
            insertConfig(duplicatedConfig)
        }
        val actual = globalDatabaseBuilder.serverConfigurationDAO.configById(config1.id)

        assertEquals(config1, actual)
        assertNotEquals(duplicatedConfig, actual)
    }

    @Test
    fun givenAlreadyStoredServerConfig_whenInsertingNewOneWithTheSameTitle_thenNothingChanges() = runTest {
        val newLinks = config1.links.copy(title = "title")
        val duplicatedConfig = config1.copy(links = newLinks)
        insertConfig(config1)
        assertFails {
            insertConfig(duplicatedConfig)
        }

        val actual = globalDatabaseBuilder.serverConfigurationDAO.configById(config1.id)

        assertEquals(config1, actual)
        assertNotEquals(duplicatedConfig, actual)
    }

    @Test
    fun givenAlreadyStoredServerConfig_whenInsertingNewOneWithTheSameWSUrl_thenNothingChanges() = runTest {
        val newLinks = config1.links.copy(website = "ws_de.berlin.com")
        val duplicatedConfig = config1.copy(links = newLinks)
        insertConfig(config1)
        assertFails {
            insertConfig(duplicatedConfig)
        }

        val actual = globalDatabaseBuilder.serverConfigurationDAO.configById(config1.id)

        assertEquals(config1, actual)
        assertNotEquals(duplicatedConfig, actual)
    }

    @Test
    fun givenAlreadyStoredServerConfig_whenInsertingNewOneWithTheSameDomain_thenNothingChanges() = runTest {
        val newMetaData = config1.metaData.copy(domain = "new_domain")
        val duplicatedConfig = config1.copy(metaData = newMetaData)
        insertConfig(config1)
        assertFails {
            insertConfig(duplicatedConfig)
        }

        val actual = globalDatabaseBuilder.serverConfigurationDAO.configById(config1.id)

        assertEquals(config1, actual)
        assertNotEquals(duplicatedConfig, actual)
    }

    @Test
    fun givenExistingConfig_thenItCanBeDeleted() = runTest {
        insertConfig(config1)
        globalDatabaseBuilder.serverConfigurationDAO.deleteById(config1.id)

        val result = globalDatabaseBuilder.serverConfigurationDAO.allConfig()
        assertEquals(0, result.size)
    }

    @Test
    fun givenMultipleStoredConfig_thenItCanBeObservedAsFlow() = runTest {
        val expect = listOf(config1, config2, config3)
        expect.forEach { insertConfig(it) }

        val actual = globalDatabaseBuilder.serverConfigurationDAO.allConfigFlow().first()

        assertEquals(expect, actual)
    }

    @Test
    fun givenNewApiVersion_thenItCanBeUpdated() = runTest {
        val oldConfig = config1.copy(
            metaData = config1.metaData.copy(
                apiVersion = 1,
                federation = false
            ),
        )

        val newVersion = config1.metaData.copy(
            apiVersion = 2,
            federation = true
        )

        val expected = oldConfig.copy(metaData = newVersion)

        insertConfig(oldConfig)
        globalDatabaseBuilder.serverConfigurationDAO.updateServerMetaData(
            id = oldConfig.id,
            federation = true,
            commonApiVersion = 2
        )
        globalDatabaseBuilder.serverConfigurationDAO.configById(oldConfig.id)
            .also { actual ->
                assertEquals(expected.metaData.federation, actual!!.metaData.federation)
                assertEquals(expected.metaData.apiVersion, actual!!.metaData.apiVersion)
            }
    }

    @Test
    fun givenNewApiVersionAndDomain_thenItCanBeUpdated() = runTest {
        insertConfig(config1)
        val newVersion = 2
        val newDomain = "new.domain.de"
        val expected = config1.copy(metaData = config1.metaData.copy(apiVersion = newVersion, domain = newDomain))

        globalDatabaseBuilder.serverConfigurationDAO.updateApiVersionAndDomain(config1.id, newDomain, newVersion)
        val actual = globalDatabaseBuilder.serverConfigurationDAO.configById(config1.id)
        assertEquals(expected, actual)
    }

    @Test
    fun givenFederationEnabled_thenItCanBeUpdated() = runTest {
        insertConfig(
            config1.copy(metaData = config1.metaData.copy(federation = true))
        )
        val expected = config1.copy(metaData = config1.metaData.copy(federation = true))

        globalDatabaseBuilder.serverConfigurationDAO.setFederationToTrue(config1.id)
        val actual = globalDatabaseBuilder.serverConfigurationDAO.configById(config1.id)
        assertEquals(expected, actual)
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
