package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import io.mockative.Mock
<<<<<<< HEAD
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
=======
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
>>>>>>> c5c2468502 (chore: bulletproofing crypto box to cc migration (WPB-14250) (🍒4.6) (#3136))
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProteusMigrationRecoveryHandlerTest {

    @Test
    fun givenGettingOrCreatingAProteusClient_whenMigrationPerformedAndFails_thenCatchErrorAndStartRecovery() = runTest {
        // given
        val (arrangement, proteusMigrationRecoveryHandler) = Arrangement().arrange()

        // when
        val clearLocalFiles: suspend () -> Unit = { }
        proteusMigrationRecoveryHandler.clearClientData(clearLocalFiles)

        // then
<<<<<<< HEAD
        coVerify { arrangement.logoutUseCase(LogoutReason.MIGRATION_TO_CC_FAILED, true) }.wasInvoked(once)
=======
        verify(arrangement.logoutUseCase)
            .coroutine { invoke(LogoutReason.MIGRATION_TO_CC_FAILED, true) }
            .wasInvoked(exactly = once)
>>>>>>> c5c2468502 (chore: bulletproofing crypto box to cc migration (WPB-14250) (🍒4.6) (#3136))
    }

    private class Arrangement {

        @Mock
        val logoutUseCase = mock(LogoutUseCase::class)

        fun arrange() = this to ProteusMigrationRecoveryHandlerImpl(
            lazy { logoutUseCase }
        )
    }
}
