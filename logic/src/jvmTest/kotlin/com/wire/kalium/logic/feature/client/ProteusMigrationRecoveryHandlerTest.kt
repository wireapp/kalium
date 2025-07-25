package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
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
        coVerify { arrangement.logoutUseCase(LogoutReason.MIGRATION_TO_CC_FAILED, true) }.wasInvoked(once)
    }

    private class Arrangement {

        val logoutUseCase = mock(LogoutUseCase::class)

        fun arrange() = this to ProteusMigrationRecoveryHandlerImpl(
            lazy { logoutUseCase }
        )
    }
}
