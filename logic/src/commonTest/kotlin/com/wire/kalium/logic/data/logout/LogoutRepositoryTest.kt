package com.wire.kalium.logic.data.logout

import app.cash.turbine.test
import com.wire.kalium.network.api.user.logout.LogoutApi
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LogoutRepositoryTest {

    @Test
    fun givenLogoutFlowIsBeingObserved_whenInvokingOnLogout_thenFlowShouldEmit() = runTest {
        // Given
        val reason = LogoutReason.USER_INTENTION

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

    private class Arrangement {

        @Mock
        private val logoutApi = mock(classOf<LogoutApi>())

        private val logoutDataSource = LogoutDataSource(logoutApi)

        fun arrange() = this to logoutDataSource

    }

}
