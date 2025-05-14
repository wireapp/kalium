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

package com.wire.kalium.logic.data.logout

import app.cash.turbine.test
import com.wire.kalium.network.api.base.authenticated.logout.LogoutApi
import com.wire.kalium.persistence.dao.MetadataDAO
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LogoutRepositoryTest {

    @Test
    fun givenLogoutFlowIsBeingObserved_whenInvokingOnLogout_thenFlowShouldEmit() = runTest {
        // Given
        val reason = LogoutReason.SELF_HARD_LOGOUT

        val (_, logoutRepository) = Arrangement().arrange()

        logoutRepository.observeLogout().test {

            // When
            logoutRepository.onLogout(reason)

            // Then
            assertEquals(reason, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenOnLogoutWasNotInvoked_whenObservingLogoutFlow_thenNoEventsShouldBePresent() = runTest {
        // Given - NOOP

        val (_, logoutRepository) = Arrangement().arrange()

        // When
        logoutRepository.observeLogout().test {

            // Then
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenFailureHappensOnLogoutReasonCollection_whenCollectingAgain_thenShouldEmitNewValues() = runTest {
        val (_, logoutRepository) = Arrangement().arrange()

        logoutRepository.onLogout(LogoutReason.DELETED_ACCOUNT)
        try {
            logoutRepository.observeLogout().collect { throw IllegalStateException() }
        } catch (ignored: Throwable) {
            // Ignored, really
        }

        logoutRepository.observeLogout().test {
            logoutRepository.onLogout(LogoutReason.SELF_HARD_LOGOUT)
            assertEquals(LogoutReason.SELF_HARD_LOGOUT, awaitItem())
            logoutRepository.onLogout(LogoutReason.SESSION_EXPIRED)
            assertEquals(LogoutReason.SESSION_EXPIRED, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class Arrangement {
        private val logoutApi = mock(LogoutApi::class)
        private val metadataDAO = mock(MetadataDAO::class)

        private val logoutDataSource = LogoutDataSource(logoutApi, metadataDAO)

        fun arrange() = this to logoutDataSource

    }

}
