package com.wire.kalium.persistence.dao_kalium_db

import com.wire.kalium.persistence.GlobalDBBaseTest
import com.wire.kalium.persistence.db.GlobalDatabaseProvider
import com.wire.kalium.persistence.model.ServerConfigEntity
import com.wire.kalium.persistence.utils.stubs.newServerConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class CurrentAuthenticationServerDAOTest: GlobalDBBaseTest() {

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
    fun givenValidServerConfig_thenItCanBeSetAsTheCurrentAuthenticationServer() = runTest {
        val serverConfigEntity = newServerConfig(1)
        val expected = serverConfigEntity.id
        insertConfig(serverConfigEntity)
        db.currentAuthenticationServerDAO.update(expected)

        db.currentAuthenticationServerDAO.currentConfigId().also { actual ->
            assertEquals(expected, actual)
        }
    }

    @Test
    fun givenMultipleServerConfig_whenUpdatingCurrentAuthServer_thenItCanBeUpdated() = runTest {
        val serverConfigEntity1 = newServerConfig(1)
        val serverConfigEntity2 = newServerConfig(2)

        insertConfig(serverConfigEntity1)
        insertConfig(serverConfigEntity2)

        db.currentAuthenticationServerDAO.update(serverConfigEntity1.id)
        db.currentAuthenticationServerDAO.currentConfigId().also { actual ->
            assertEquals(serverConfigEntity1.id, actual)
        }

        db.currentAuthenticationServerDAO.update(serverConfigEntity2.id)
        db.currentAuthenticationServerDAO.currentConfigId().also { actual ->
            assertEquals(serverConfigEntity2.id, actual)
        }
    }

    private fun insertConfig(serverConfigEntity: ServerConfigEntity) {
        with(serverConfigEntity) {
            db.serverConfigurationDAO.insert(
                id,
                apiBaseUrl,
                accountBaseUrl,
                webSocketBaseUrl,
                blackListUrl,
                teamsUrl,
                websiteUrl,
                title,
                federation,
                domain,
                commonApiVersion
            )
        }
    }
}
