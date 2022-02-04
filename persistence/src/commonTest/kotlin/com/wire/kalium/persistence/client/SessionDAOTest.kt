package com.wire.kalium.persistence.client

import com.russhwolf.settings.MockSettings
import com.russhwolf.settings.Settings
import com.wire.kalium.persistence.dao.UserId
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import com.wire.kalium.persistence.kmm_settings.KaliumPreferencesSettings
import com.wire.kalium.persistence.model.NetworkConfig
import com.wire.kalium.persistence.model.PersistenceSession
import com.wire.kalium.persistence.model.PreferencesResult
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionDAOTest {

    private val settings: Settings = MockSettings()

    private val kaliumPreferences: KaliumPreferences = KaliumPreferencesSettings(settings)
    private val sessionDAO: SessionDAO = SessionDAOImpl(kaliumPreferences)

    @BeforeTest
    fun setUp() {
        settings.clear()
    }

    @Test
    fun givenASSession_WhenCallingAddSession_ThenTheSessionCanBeStoredLocally() = runTest {
        val persistenceSession =
            PersistenceSession(
                "user_id_1",
                "JWT",
                Random.nextBytes(32).decodeToString(),
                Random.nextBytes(32).decodeToString(),
                randomNetworkConfig()
            )
        val sessionsMap = mapOf(persistenceSession.userId to persistenceSession)
        sessionDAO.addSession(persistenceSession)

        assertEquals(PreferencesResult.Success(sessionsMap), sessionDAO.allSessions())
    }

    @Test
    fun givenAnExistingSession_WhenCallingDeleteSession_ThenItWillBeRemoved() = runTest {
        val session1 = PersistenceSession(
            "user_id_1",
            "JWT",
            Random.nextBytes(32).decodeToString(),
            Random.nextBytes(32).decodeToString(),
            randomNetworkConfig()
        )
        val sessionToDelete =
            PersistenceSession(
                "user_id_2",
                "JWT",
                Random.nextBytes(32).decodeToString(),
                Random.nextBytes(32).decodeToString(),
                randomNetworkConfig()
            )

        val sessionsMapExpectedValue =
            PreferencesResult.Success(
                mapOf(
                    session1.userId to session1,
                    sessionToDelete.userId to sessionToDelete
                )
            )
        val afterDeleteExpectedValue = PreferencesResult.Success(mapOf(session1.userId to session1))

        sessionDAO.addSession(session1)
        sessionDAO.addSession(sessionToDelete)

        assertEquals(sessionsMapExpectedValue, sessionDAO.allSessions())
        // delete session
        sessionDAO.deleteSession(sessionToDelete.userId)
        assertEquals(afterDeleteExpectedValue, sessionDAO.allSessions())
    }

    @Test
    fun givenAUserId_WhenCallingUpdateCurrentSession_ThenItWillBeStoredLocally() = runTest {
        assertNull(sessionDAO.currentSession())
        val session1 =
            PersistenceSession(
                "user_id_1",
                "Bearer",
                Random.nextBytes(32).decodeToString(),
                Random.nextBytes(32).decodeToString(),
                randomNetworkConfig()
            )

        val session2 =
            PersistenceSession(
                "user_id_2",
                "Bearer",
                Random.nextBytes(32).decodeToString(),
                Random.nextBytes(32).decodeToString(),
                randomNetworkConfig()
            )

        sessionDAO.addSession(session1)
        sessionDAO.addSession(session2)

        sessionDAO.updateCurrentSession("user_id_1")

        assertEquals(session1, sessionDAO.currentSession())
    }

    private companion object {
        val randomString get() = Random.nextBytes(64).decodeToString()
        fun randomNetworkConfig(): NetworkConfig =
            NetworkConfig(randomString, randomString, randomString, randomString, randomString, randomString)

    }

}
