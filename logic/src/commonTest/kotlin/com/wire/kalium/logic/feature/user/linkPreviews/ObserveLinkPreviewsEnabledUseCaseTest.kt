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
package com.wire.kalium.logic.feature.user.linkPreviews

import app.cash.turbine.test
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ObserveLinkPreviewsEnabledUseCaseTest {

    @Test
    fun givenALinkPreviewsState_whenInvokingObserveLinkPreviewsEnabled_thenShouldReturnSuccessResult() = runTest {
        val (arrangement, observeLinkPreviewsEnabled) = Arrangement()
            .withSuccessfulState()
            .arrange()

        val result = observeLinkPreviewsEnabled()

        result.test {
            assertTrue(awaitItem())
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.userPropertyRepository.observeLinkPreviewsStatus()
            }
            awaitComplete()
        }
    }

    @Test
    fun givenALinkPreviewsState_whenFailureInvokingObserveLinkPreviewsEnabled_thenShouldReturnFalseFallback() = runTest {
        val (arrangement, observeLinkPreviewsEnabled) = Arrangement()
            .withFailureState()
            .arrange()

        val result = observeLinkPreviewsEnabled()

        result.test {
            assertFalse(awaitItem())
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.userPropertyRepository.observeLinkPreviewsStatus()
            }
            awaitComplete()
        }
    }

    private class Arrangement {
        val userPropertyRepository = mock<UserPropertyRepository>()
        val observeLinkPreviewsEnabled = ObserveLinkPreviewsEnabledUseCaseImpl(userPropertyRepository)

        suspend fun withSuccessfulState() = apply {
            everySuspend { userPropertyRepository.observeLinkPreviewsStatus() } returns flowOf(Either.Right(true))
        }

        suspend fun withFailureState() = apply {
            everySuspend { userPropertyRepository.observeLinkPreviewsStatus() } returns flowOf(Either.Left(StorageFailure.DataNotFound))
        }

        fun arrange() = this to observeLinkPreviewsEnabled
    }
}
