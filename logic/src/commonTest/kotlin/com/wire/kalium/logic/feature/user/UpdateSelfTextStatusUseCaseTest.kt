/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.feature.user

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.user.AccountRepository
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class UpdateSelfTextStatusUseCaseTest {
    @Test
    fun unicodeCodePointsAtTheBackendLimitAreAccepted() = runTest {
        val repository = mock<AccountRepository>(MockMode.autoUnit)
        everySuspend { repository.updateSelfUserTextStatus(any()) } returns Either.Right(Unit)
        val status = "🎧".repeat(256)

        UpdateSelfTextStatusUseCase(repository)(status).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) { repository.updateSelfUserTextStatus(status) }
    }

    @Test
    fun oversizedStatusFailsBeforeChangingTheRepository() = runTest {
        val repository = mock<AccountRepository>(MockMode.autoUnit)

        UpdateSelfTextStatusUseCase(repository)("🎧".repeat(257)).shouldFail()

        verifySuspend(VerifyMode.exactly(0)) { repository.updateSelfUserTextStatus(any()) }
    }
}
