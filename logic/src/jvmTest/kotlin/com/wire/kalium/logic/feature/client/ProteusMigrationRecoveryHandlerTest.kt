package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import dev.mokkery.MockMode
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.logoutUseCase(eq(LogoutReason.MIGRATION_TO_CC_FAILED), eq(true))
        }
    }

    private class Arrangement {

        val logoutUseCase = mock<LogoutUseCase>(mode = MockMode.autoUnit)

        fun arrange() = this to ProteusMigrationRecoveryHandlerImpl(
            lazy { logoutUseCase }
        )
    }
}
