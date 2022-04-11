package com.wire.kalium.persistence.dao_kalium_db

import com.wire.kalium.persistence.KaliumDBBaseTest
import com.wire.kalium.persistence.db.KaliumDatabaseProvider
import com.wire.kalium.persistence.model.ServerConfigEntity
import com.wire.kalium.persistence.utils.stubs.newServerConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ServerConfigurationDAOTest : KaliumDBBaseTest() {

    private val config1 = newServerConfig(id = 1)
    private val config2 = newServerConfig(id = 2)
    private val config3 = newServerConfig(id = 3)

    lateinit var db: KaliumDatabaseProvider

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
        val actual = db.serverConfigurationDAO.configByTitle(expect.title)

        assertEquals(expect, actual)
    }

    @Test
    fun givenExistingConfig_thenItCanBeDeleted() {
        insertConfig(config1)
        db.serverConfigurationDAO.deleteByTitle(config1.title)

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



    private fun insertConfig(serverConfigEntity: ServerConfigEntity) {
        with(serverConfigEntity) {
            db.serverConfigurationDAO.insert(
                apiBaseUrl, accountBaseUrl, webSocketBaseUrl, blackListUrl, teamsUrl, websiteUrl, title
            )
        }
    }
}
