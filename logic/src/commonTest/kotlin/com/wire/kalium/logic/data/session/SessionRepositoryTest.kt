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

package com.wire.kalium.logic.data.session

import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.persistence.client.AuthTokenStorage
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.daokaliumdb.AccountInfoEntity
import com.wire.kalium.persistence.daokaliumdb.AccountsDAO
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAO
import io.mockative.Mock
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow

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

    @Suppress("UnusedPrivateClass")
    private class Arrangement {

        val sessionMapper = MapperProvider.sessionMapper()

        @Mock
        val accountsDAO: AccountsDAO = mock(AccountsDAO::class)

        @Mock
        val authTokenStorage: AuthTokenStorage = mock(AuthTokenStorage::class)

        @Mock
        val kaliumConfigs: KaliumConfigs = mock(KaliumConfigs::class)

        @Mock
        val serverConfigurationDAO: ServerConfigurationDAO = mock(ServerConfigurationDAO::class)

        private val sessionRepository =
            SessionDataSource(accountsDAO, authTokenStorage, serverConfigurationDAO, kaliumConfigs)

        val validAccountIndoEntity = AccountInfoEntity(userIDEntity = UserIDEntity("1", "domain"), null)

        val accountInfoValid = sessionMapper.fromAccountInfoEntity(validAccountIndoEntity)

        suspend fun withObserveAllAccountList(allSessionsFlow: Flow<List<AccountInfoEntity>>) = apply {
            given(accountsDAO).coroutine { accountsDAO.observeAllAccountList() }.then { allSessionsFlow }
        }

        internal fun arrange() = this to sessionRepository
    }
}
