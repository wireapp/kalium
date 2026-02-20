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

import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.persistence.client.AuthTokenStorage
import com.wire.kalium.persistence.daokaliumdb.AccountsDAO
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAO
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionRepositoryTest {

    @Test
    fun givenNativePushValue_whenPersistingFlag_thenValueIsWrittenToServerConfiguration() = runTest {
        val (arrangement, sessionRepository) = Arrangement().arrange()

        sessionRepository.setNativePushEnabledForUser(TEST_USER_ID, false).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.serverConfigurationDAO.updateNativePushEnabledByUser(eq(TEST_USER_ID.toDao()), eq(false))
        }
    }

    @Test
    fun givenNativePushDisabledOnServerConfig_whenReadingFlag_thenDisabledIsReturned() = runTest {
        val (arrangement, sessionRepository) = Arrangement()
            .withNativePushEnabledByUser(false)
            .arrange()

        sessionRepository.isNativePushEnabledForUser(TEST_USER_ID).shouldSucceed {
            assertEquals(false, it)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.serverConfigurationDAO.nativePushEnabledByUser(eq(TEST_USER_ID.toDao()))
        }
    }

    @Test
    fun givenNoServerConfigForUser_whenReadingFlag_thenNativePushDefaultsToEnabled() = runTest {
        val (arrangement, sessionRepository) = Arrangement()
            .withNativePushEnabledByUser(null)
            .arrange()

        sessionRepository.isNativePushEnabledForUser(TEST_USER_ID).shouldSucceed {
            assertEquals(true, it)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.serverConfigurationDAO.nativePushEnabledByUser(eq(TEST_USER_ID.toDao()))
        }
    }

    private class Arrangement {
        val accountsDAO: AccountsDAO = mock(mode = MockMode.autoUnit)
        val authTokenStorage: AuthTokenStorage = mock(mode = MockMode.autoUnit)
        val serverConfigurationDAO: ServerConfigurationDAO = mock(mode = MockMode.autoUnit)

        private val sessionRepository = SessionDataSource(
            accountsDAO = accountsDAO,
            authTokenStorage = authTokenStorage,
            serverConfigDAO = serverConfigurationDAO
        )

        suspend fun withNativePushEnabledByUser(enabled: Boolean?) = apply {
            everySuspend {
                serverConfigurationDAO.nativePushEnabledByUser(any())
            } returns enabled
        }

        suspend fun arrange() = this to sessionRepository
    }

    companion object {
        private val TEST_USER_ID = UserId(value = "self", domain = "wire.com")
    }
}
