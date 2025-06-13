package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CoreCryptoMigrationRecoveryHandlerTest {

    @Test
    fun givenGettingOrCreatingClient_whenMigrationPerformedAndFails_thenCatchErrorAndStartRecovery() = runTest {
        // given
        val (arrangement, migrationRecoveryHandler) = Arrangement().arrange()

        // when
        val clearLocalFiles: suspend () -> Unit = { }
        migrationRecoveryHandler.clearClientData(clearLocalFiles)

        // then
        coVerify { arrangement.logoutUseCase(LogoutReason.MIGRATION_TO_CC_FAILED, true) }.wasInvoked(once)
    }

    private class Arrangement {

        val logoutUseCase = mock(LogoutUseCase::class)

        fun arrange() = this to CoreCryptoMigrationRecoveryHandlerImpl(
            lazy { logoutUseCase }
        )
    }
}
