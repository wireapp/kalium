/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.feature.user

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.user.AccountRepository
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class UpdateSelfTextStatusUseCaseTest {
    @Test
    fun unicodeCodePointsAtTheBackendLimitAreAccepted() = runTest {
        val repository = mock<AccountRepository>(MockMode.autoUnit)
        everySuspend { repository.updateSelfUserTextStatus(any()) } returns Either.Right(Unit)
        val status = "🎧".repeat(256)

        assertEquals(UpdateSelfTextStatusUseCase.Result.Success, UpdateSelfTextStatusUseCase(repository)(status))

        verifySuspend(VerifyMode.exactly(1)) { repository.updateSelfUserTextStatus(status) }
    }

    @Test
    fun oversizedStatusFailsBeforeChangingTheRepository() = runTest {
        val repository = mock<AccountRepository>(MockMode.autoUnit)

        assertIs<UpdateSelfTextStatusUseCase.Result.Failure>(UpdateSelfTextStatusUseCase(repository)("🎧".repeat(257)))

        verifySuspend(VerifyMode.exactly(0)) { repository.updateSelfUserTextStatus(any()) }
    }

    @Test
    fun nullAndBlankStatusesClearTheStoredStatus() = runTest {
        val repository = mock<AccountRepository>(MockMode.autoUnit)
        everySuspend { repository.updateSelfUserTextStatus(null) } returns Either.Right(Unit)
        val updateStatus = UpdateSelfTextStatusUseCase(repository)

        listOf(null, "", " \n\t ").forEach { status ->
            assertEquals(UpdateSelfTextStatusUseCase.Result.Success, updateStatus(status))
        }

        verifySuspend(VerifyMode.exactly(3)) { repository.updateSelfUserTextStatus(null) }
    }

    @Test
    fun repositoryFailureIsPreservedInThePublicResult() = runTest {
        val repository = mock<AccountRepository>(MockMode.autoUnit)
        val failure = CoreFailure.Unknown(null)
        everySuspend { repository.updateSelfUserTextStatus(any()) } returns Either.Left(failure)

        val result = UpdateSelfTextStatusUseCase(repository)("In a meeting")

        assertSame(failure, assertIs<UpdateSelfTextStatusUseCase.Result.Failure>(result).coreFailure)
    }
}
