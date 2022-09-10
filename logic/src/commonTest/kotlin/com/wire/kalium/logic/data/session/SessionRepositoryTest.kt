package com.wire.kalium.logic.data.session

import app.cash.turbine.test
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.util.stubs.newServerConfigEntity
import com.wire.kalium.persistence.client.SessionStorage
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.model.AuthSessionEntity
import com.wire.kalium.persistence.model.LogoutReason
import com.wire.kalium.persistence.model.SsoIdEntity
import io.mockative.Mock
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SessionRepositoryTest {

    @Test
    fun givenASession_whenObservingAllSessions_thenChangesArePropagated() = runTest {
        val sessionsStateFlow = MutableStateFlow(mapOf<UserIDEntity, AuthSessionEntity>())
        val (arrangement, sessionRepository) = Arrangement()
            .arrange()
        val sessionsMapExpectedValue = listOf(arrangement.sessionValid)
        sessionRepository.allSessionsFlow().test {
            assertEquals(listOf(), awaitItem())
            sessionsStateFlow.emit(mapOf(arrangement.sessionEntityValid.userId to arrangement.sessionEntityValid))
            assertEquals(sessionsMapExpectedValue, awaitItem())
            sessionsStateFlow.emit(mapOf())
            assertEquals(listOf(), awaitItem())
        }
    }

    @Test
    fun givenASession_whenObservingAllValidSessions_thenOnlyValidOnesArePropagated() = runTest {
        val sessionsStateFlow = MutableStateFlow(mapOf<UserIDEntity, AuthSessionEntity>())
        val (arrangement, sessionRepository) = Arrangement()
            .arrange()
        val sessionsMapExpectedValue = listOf(arrangement.sessionValid)
        sessionRepository.allValidSessionsFlow().test {
            assertEquals(listOf(), awaitItem())
            sessionsStateFlow.emit(mapOf(
                arrangement.sessionEntityValid.userId to arrangement.sessionEntityValid,
                arrangement.sessionEntityInvalid.userId to arrangement.sessionEntityInvalid
            ))
            assertEquals(sessionsMapExpectedValue, awaitItem())
        }
    }

    class Arrangement {

        val sessionMapper = MapperProvider.sessionMapper()
        val idMapper = MapperProvider.idMapper()

        private val sessionRepository = SessionDataSource(TODO(), TODO(), TODO(), sessionMapper, idMapper)


        val sessionEntityValid = AuthSessionEntity.Valid(
            QualifiedIDEntity("user_id_valid", "user_domain"),
            "JWT",
            Random.nextBytes(32).decodeToString(),
            Random.nextBytes(32).decodeToString(),
            newServerConfigEntity(1).links,
            SsoIdEntity(null, null, null)
        )
        val sessionEntityInvalid = AuthSessionEntity.Invalid(
            QualifiedIDEntity("user_id_invalid", "user_domain"),
            newServerConfigEntity(1).links,
            LogoutReason.DELETED_ACCOUNT,
            true,
            SsoIdEntity(null, null, null)
        )
        val sessionValid = sessionMapper.fromPersistenceSession(sessionEntityValid)

        internal fun arrange() = this to sessionRepository
    }
}
