package com.wire.kalium.persistence.client

import app.cash.turbine.test
import com.russhwolf.settings.MockSettings
import com.russhwolf.settings.Settings
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import com.wire.kalium.persistence.kmm_settings.KaliumPreferencesSettings
import com.wire.kalium.persistence.model.AuthSessionEntity
import com.wire.kalium.persistence.model.ServerConfigEntity
import com.wire.kalium.persistence.utils.stubs.newServerConfig
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
        val authSessionEntity =
            AuthSessionEntity(
                QualifiedIDEntity("user_id_1", "user_domain_1"),
                "JWT",
                Random.nextBytes(32).decodeToString(),
                Random.nextBytes(32).decodeToString(),
                TEST_SERVER_CONFIG.links
            )
        val sessionsMap = mapOf(authSessionEntity.userId to authSessionEntity)
        sessionStorage.addSession(authSessionEntity)

        assertEquals(sessionsMap, sessionStorage.allSessions())
    }

    @Test
    fun givenAnExistingSession_WhenCallingDeleteSession_ThenItWillBeRemoved() = runTest {
        val session1 = AuthSessionEntity(
            QualifiedIDEntity("user_id_1", "user_domain_1"),
            "JWT",
            Random.nextBytes(32).decodeToString(),
            Random.nextBytes(32).decodeToString(),
            TEST_SERVER_CONFIG.links
        )
        val sessionToDelete =
            AuthSessionEntity(
                QualifiedIDEntity("user_id_2", "user_domain_2"),
                "JWT",
                Random.nextBytes(32).decodeToString(),
                Random.nextBytes(32).decodeToString(),
                TEST_SERVER_CONFIG.links
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
        sessionStorage.currentSessionFlow().test {
            assertNull(awaitItem())
        }
        val session1 =
            AuthSessionEntity(
                QualifiedIDEntity("user_id_1", "user_domain_1"),
                "Bearer",
                Random.nextBytes(32).decodeToString(),
                Random.nextBytes(32).decodeToString(),
                TEST_SERVER_CONFIG.links
            )

        val session2 =
            AuthSessionEntity(
                QualifiedIDEntity("user_id_2", "user_domain_2"),
                "Bearer",
                Random.nextBytes(32).decodeToString(),
                Random.nextBytes(32).decodeToString(),
                TEST_SERVER_CONFIG.links
            )

        sessionStorage.addSession(session1)
        sessionStorage.addSession(session2)

        sessionStorage.setCurrentSession(QualifiedIDEntity("user_id_1", "user_domain_1"))

        assertEquals(session1, sessionStorage.currentSession())
        sessionStorage.currentSessionFlow().test {
            assertEquals(session1, awaitItem())
        }
    }

    private companion object {
        val TEST_SERVER_CONFIG: ServerConfigEntity = newServerConfig(1)
    }

}
