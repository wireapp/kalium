package com.wire.kalium.logic.data.session

import app.cash.turbine.test
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.auth.AccountInfo
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.persistence.client.AuthTokenStorage
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao_kalium_db.AccountInfoEntity
import com.wire.kalium.persistence.dao_kalium_db.AccountsDAO
import io.mockative.Mock
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SessionRepositoryTest {

    /*
    @Test
    fun givenASession_whenObservingAllSessions_thenChangesArePropagated() = runTest {
        val sessionsStateFlow = MutableStateFlow(listOf(AccountInfoEntity(userIDEntity = UserIDEntity("1", "domain"), null)))
        val (arrangement, sessionRepository) = Arrangement()
            .withObserveAllAccountList(sessionsStateFlow)
            .arrange()

        val sessionsMapExpectedValue = listOf(arrangement.accountInfoValid)
        sessionRepository.allSessionsFlow().test {
            assertEquals(listOf(), awaitItem())
            sessionsStateFlow.emit(listOf(arrangement.validAccountIndoEntity))
            assertEquals(sessionsMapExpectedValue, awaitItem())
            sessionsStateFlow.emit(listOf())
            assertEquals(listOf(), awaitItem())
        }
    }
     */

    /*
    @Test
    fun givenASession_whenObservingAllValidSessions_thenOnlyValidOnesArePropagated() = runTest {
        val sessionsStateFlow = MutableStateFlow(mapOf<UserIDEntity, AuthSessionEntity>())
        val (arrangement, sessionRepository) = Arrangement()
            .arrange()
        val sessionsMapExpectedValue = listOf(arrangement.sessionValid)
        sessionRepository.allValidSessionsFlow().test {
            assertEquals(listOf(), awaitItem())
            sessionsStateFlow.emit(
                mapOf(
                    arrangement.sessionEntityValid.userId to arrangement.sessionEntityValid,
                    arrangement.sessionEntityInvalid.userId to arrangement.sessionEntityInvalid
                )
            )
            assertEquals(sessionsMapExpectedValue, awaitItem())
        }
    }

     */

    private class Arrangement {

        val sessionMapper = MapperProvider.sessionMapper()
        val idMapper = MapperProvider.idMapper()

        @Mock
        val accountsDAO: AccountsDAO = mock(AccountsDAO::class)

        @Mock
        val authTokenStorage: AuthTokenStorage = mock(AuthTokenStorage::class)

        @Mock
        val serverConfigRepository: ServerConfigRepository = mock(ServerConfigRepository::class)

        private val sessionRepository = SessionDataSource(accountsDAO, authTokenStorage, serverConfigRepository, sessionMapper, idMapper)


        val validAccountIndoEntity = AccountInfoEntity(userIDEntity = UserIDEntity("1", "domain"), null)

        val accountInfoValid = sessionMapper.fromAccountInfoEntity(validAccountIndoEntity)

        suspend fun withObserveAllAccountList(allSessionsFlow: Flow<List<AccountInfoEntity>>) = apply {
            given(accountsDAO).coroutine { accountsDAO.observeAllAccountList() }.then { allSessionsFlow }
        }

        internal fun arrange() = this to sessionRepository
    }
}
