package com.wire.kalium.persistence.dao_kalium_db

import com.wire.kalium.persistence.GlobalDBBaseTest
import com.wire.kalium.persistence.db.GlobalDatabaseProvider
import com.wire.kalium.persistence.model.ServerConfigEntity
import com.wire.kalium.persistence.utils.stubs.newServerConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ServerConfigurationDAOTest : GlobalDBBaseTest() {

    private val config1 = newServerConfig(id = 1)
    private val config2 = newServerConfig(id = 2)
    private val config3 = newServerConfig(id = 3)

    lateinit var db: GlobalDatabaseProvider

    @BeforeTest
    fun setup() {
        db = createDatabase()
    }

    @AfterTest
    fun nuke() {
        deleteDatabase()
    }

    @Test
    fun givenServerConfig_ThenItCanBeInsertedAndRetrieved() {
        val expect = config1
        insertConfig(expect)
        val actual = db.serverConfigurationDAO.configById(expect.id)

        assertEquals(expect, actual)
    }

    @Test
    fun givenAlreadyStoredServerConfig_whenInsertingNewOneWithTheSameApiBaseUrl_thenNothingChanges() {
        val newLinks = config1.links.copy(api = "new_base_url.com")
        val duplicatedConfig = config1.copy(links = newLinks)
        insertConfig(config1)
        insertConfig(duplicatedConfig)

        val actual = db.serverConfigurationDAO.configById(config1.id)

        assertEquals(config1, actual)
        assertNotEquals(duplicatedConfig, actual)
    }

    @Test
    fun givenAlreadyStoredServerConfig_whenInsertingNewOneWithTheSameTitle_thenNothingChanges() {
        val newLinks = config1.links.copy(title = "title")
        val duplicatedConfig = config1.copy(links = newLinks)
        insertConfig(config1)
        insertConfig(duplicatedConfig)

        val actual = db.serverConfigurationDAO.configById(config1.id)

        assertEquals(config1, actual)
        assertNotEquals(duplicatedConfig, actual)
    }


    @Test
    fun givenAlreadyStoredServerConfig_whenInsertingNewOneWithTheSameWSUrl_thenNothingChanges() {
        val newLinks = config1.links.copy(website = "ws_de.berlin.com")
        val duplicatedConfig = config1.copy(links = newLinks)
        insertConfig(config1)
        insertConfig(duplicatedConfig)

        val actual = db.serverConfigurationDAO.configById(config1.id)

        assertEquals(config1, actual)
        assertNotEquals(duplicatedConfig, actual)
    }


    @Test
    fun givenAlreadyStoredServerConfig_whenInsertingNewOneWithTheSameDomain_thenNothingChanges() {
        val newMetaData = config1.metaData.copy(domain = "new_domain")
        val duplicatedConfig = config1.copy(metaData = newMetaData)
        insertConfig(config1)
        insertConfig(duplicatedConfig)

        val actual = db.serverConfigurationDAO.configById(config1.id)

        assertEquals(config1, actual)
        assertNotEquals(duplicatedConfig, actual)
    }


    @Test
    fun givenExistingConfig_thenItCanBeDeleted() {
        insertConfig(config1)
        db.serverConfigurationDAO.deleteById(config1.id)

        val result = db.serverConfigurationDAO.allConfig()
        assertEquals(0, result.size)
    }

    @Test
    fun givenMultipleStoredConfig_thenItCanBeObservedAsFlow() = runTest {
        val expect = listOf(config1, config2, config3)
        expect.forEach { insertConfig(it) }

        val actual = db.serverConfigurationDAO.allConfigFlow().first()

        assertEquals(expect, actual)
    }

    @Test
    fun givenNewApiVersion_thenItCanBeUpdated() {
        insertConfig(config1)
        val newVersion = config1.metaData.copy(apiVersion = 2)
        val expected = config1.copy(metaData = newVersion)

        db.serverConfigurationDAO.updateApiVersion(config1.id, newVersion.apiVersion)
        val actual = db.serverConfigurationDAO.configById(config1.id)
        assertEquals(expected, actual)
    }

    @Test
    fun givenNewApiVersionAndDomain_thenItCanBeUpdated() {
        insertConfig(config1)
        val newVersion = 2
        val newDomain = "new.domain.de"
        val expected = config1.copy(metaData = config1.metaData.copy(apiVersion = newVersion, domain = newDomain))

        db.serverConfigurationDAO.updateApiVersionAndDomain(config1.id, newDomain, newVersion)
        val actual = db.serverConfigurationDAO.configById(config1.id)
        assertEquals(expected, actual)
    }

    @Test
    fun givenFederationEnabled_thenItCanBeUpdated() {
        insertConfig(
            config1.copy(metaData = config1.metaData.copy(federation = true))
        )
        val expected = config1.copy(metaData = config1.metaData.copy(federation = true))

        db.serverConfigurationDAO.setFederationToTrue(config1.id)
        val actual = db.serverConfigurationDAO.configById(config1.id)
        assertEquals(expected, actual)
    }


    private fun insertConfig(serverConfigEntity: ServerConfigEntity) {
        with(serverConfigEntity) {
            db.serverConfigurationDAO.insert(
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
                    commonApiVersion = metaData.apiVersion
                )
            )
        }
    }
}
