package com.wire.kalium.persistence.client

import com.russhwolf.settings.MockSettings
import com.russhwolf.settings.Settings
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import com.wire.kalium.persistence.kmm_settings.KaliumPreferencesSettings
import com.wire.kalium.persistence.model.NetworkConfig
import com.wire.kalium.persistence.model.PersistenceSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SessionDAOTest {

    private val settings: Settings = MockSettings()

    private val kaliumPreferences: KaliumPreferences = KaliumPreferencesSettings(settings)
    private lateinit var sessionStorage: SessionStorage

    @BeforeTest
    fun setUp() {
        sessionStorage = SessionStorageImpl(kaliumPreferences)
    }

    @AfterTest
    fun clear() {
        settings.clear()
    }

    @Test
    fun givenASession_WhenCallingAddSession_ThenTheSessionCanBeStoredLocally() = runTest {
        val persistenceSession =
            PersistenceSession(
                QualifiedIDEntity("user_id_1", "user_domain_1"),
                "JWT",
                Random.nextBytes(32).decodeToString(),
                Random.nextBytes(32).decodeToString(),
                randomNetworkConfig()
            )
        val sessionsMap = mapOf(persistenceSession.userId to persistenceSession)
        sessionStorage.addSession(persistenceSession)

        assertEquals(sessionsMap, sessionStorage.allSessions())
    }

    @Test
    fun givenAnExistingSession_WhenCallingDeleteSession_ThenItWillBeRemoved() = runTest {
        val session1 = PersistenceSession(
            QualifiedIDEntity("user_id_1", "user_domain_1"),
            "JWT",
            Random.nextBytes(32).decodeToString(),
            Random.nextBytes(32).decodeToString(),
            randomNetworkConfig()
        )
        val sessionToDelete =
            PersistenceSession(
                QualifiedIDEntity("user_id_2", "user_domain_2"),
                "JWT",
                Random.nextBytes(32).decodeToString(),
                Random.nextBytes(32).decodeToString(),
                randomNetworkConfig()
            )

        val sessionsMapExpectedValue =
                mapOf(
                    session1.userId to session1,
                    sessionToDelete.userId to sessionToDelete
            )
        val afterDeleteExpectedValue = mapOf(session1.userId to session1)

        sessionStorage.addSession(session1)
        sessionStorage.addSession(sessionToDelete)

        assertEquals(sessionsMapExpectedValue, sessionStorage.allSessions())
        // delete session
        sessionStorage.deleteSession(sessionToDelete.userId)
        assertEquals(afterDeleteExpectedValue, sessionStorage.allSessions())
    }

    @Test
    fun givenAUserId_WhenCallingUpdateCurrentSession_ThenItWillBeStoredLocally() = runTest {
        assertNull(sessionStorage.currentSession())
        val session1 =
            PersistenceSession(
                QualifiedIDEntity("user_id_1", "user_domain_1"),
                "Bearer",
                Random.nextBytes(32).decodeToString(),
                Random.nextBytes(32).decodeToString(),
                randomNetworkConfig()
            )

        val session2 =
            PersistenceSession(
                QualifiedIDEntity("user_id_2", "user_domain_2"),
                "Bearer",
                Random.nextBytes(32).decodeToString(),
                Random.nextBytes(32).decodeToString(),
                randomNetworkConfig()
            )

        sessionStorage.addSession(session1)
        sessionStorage.addSession(session2)

        sessionStorage.setCurrentSession(QualifiedIDEntity("user_id_1", "user_domain_1"))

        assertEquals(session1, sessionStorage.currentSession())
    }

    private companion object {
        val randomString get() = Random.nextBytes(64).decodeToString()
        fun randomNetworkConfig(): NetworkConfig =
            NetworkConfig(randomString, randomString, randomString, randomString, randomString, randomString, "test_network_config")
    }

}
