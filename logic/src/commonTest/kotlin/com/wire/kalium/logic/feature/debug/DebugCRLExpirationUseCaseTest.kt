/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.debug

import com.wire.kalium.logic.data.e2ei.CertificateRevocationListRepository
import dev.mokkery.MockMode
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class DebugCRLExpirationUseCaseTest {

    private val certificateRevocationListRepository = mock<CertificateRevocationListRepository>(mode = MockMode.autoUnit)

    @Test
    fun givenForceExpirationIsEnabled_whenSettingDebugCRLExpiration_thenExpirationDatesAreCleared() = runTest {
        val useCase = SetDebugCRLExpirationAfterOneMinuteUseCaseImpl(certificateRevocationListRepository)

        useCase(true)

        verifySuspend(VerifyMode.exactly(1)) {
            certificateRevocationListRepository.setShouldForceCRLExpirationAfterOneMinute(true)
            certificateRevocationListRepository.clearCRLExpirationDates()
        }
    }

    @Test
    fun givenForceExpirationIsDisabled_whenSettingDebugCRLExpiration_thenExpirationDatesAreNotCleared() = runTest {
        val useCase = SetDebugCRLExpirationAfterOneMinuteUseCaseImpl(certificateRevocationListRepository)

        useCase(false)

        verifySuspend(VerifyMode.exactly(1)) {
            certificateRevocationListRepository.setShouldForceCRLExpirationAfterOneMinute(false)
        }
        verifySuspend(VerifyMode.not) {
            certificateRevocationListRepository.clearCRLExpirationDates()
        }
    }
}
