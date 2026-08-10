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
package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.isLeft
import com.wire.kalium.common.functional.isRight
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.MeetingsConfigModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class MeetingsConfigHandlerTest {

    private fun testMeetingsConfig(currentValue: Boolean, newStatus: Status?, expectedValue: Boolean, slowSyncTimerCleared: Boolean) =
        runTest {
            val (arrangement, handler) = Arrangement()
                .withMeetingsEnabled(currentValue)
                .withSetMeetingsEnabledReturning(Either.Right(Unit))
                .arrange()

            val result = handler.handle(newStatus?.let { MeetingsConfigModel(it) })

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.userConfigRepository.setMeetingsEnabled(expectedValue)
            }
            verifySuspend(VerifyMode.exactly(if (slowSyncTimerCleared) 1 else 0)) {
                arrangement.slowSyncRepository.clearLastSlowSyncCompletionInstant()
            }
            assertTrue { result.isRight() }
        }

    @Test
    fun givenMeetingsDisabled_whenHandlingEnabledStatus_thenSetMeetingsEnabledAndClearLastSlowSyncCompletionInstant() =
        testMeetingsConfig(currentValue = false, newStatus = Status.ENABLED, expectedValue = true, slowSyncTimerCleared = true)

    @Test
    fun givenMeetingsEnabled_whenHandlingEnabledStatus_thenSetMeetingsEnabledWithoutClearingLastSlowSyncCompletionInstant() =
        testMeetingsConfig(currentValue = true, newStatus = Status.ENABLED, expectedValue = true, slowSyncTimerCleared = false)

    @Test
    fun givenMeetingsEnabled_whenHandlingDisabledStatus_thenSetMeetingsDisabledWithoutClearingLastSlowSyncCompletionInstant() =
        testMeetingsConfig(currentValue = true, newStatus = Status.DISABLED, expectedValue = false, slowSyncTimerCleared = false)

    @Test
    fun givenMeetingsDisabled_whenHandlingDisabledStatus_thenSetMeetingsDisabledWithoutClearingLastSlowSyncCompletionInstant() =
        testMeetingsConfig(currentValue = false, newStatus = Status.DISABLED, expectedValue = false, slowSyncTimerCleared = false)

    @Test
    fun givenConfigIsMissing_whenHandling_thenSetMeetingsDisabledWithoutClearingLastSlowSyncCompletionInstant() =
        testMeetingsConfig(currentValue = false, newStatus = null, expectedValue = false, slowSyncTimerCleared = false)

    @Test
    fun givenSetMeetingsEnabledFails_whenHandlingEnabledConfig_thenReturnFailureWithoutClearingLastSlowSyncCompletionInstant() = runTest {
            val model = MeetingsConfigModel(Status.ENABLED)
            val (arrangement, handler) = Arrangement()
                .withMeetingsEnabled(false)
                .withSetMeetingsEnabledReturning(Either.Left(StorageFailure.DataNotFound))
                .arrange()

            val result = handler.handle(model)

            verifySuspend(VerifyMode.exactly(1)) { arrangement.userConfigRepository.setMeetingsEnabled(true) }
            verifySuspend(VerifyMode.not) { arrangement.slowSyncRepository.clearLastSlowSyncCompletionInstant() }
            assertTrue { result.isLeft() }
        }

    private class Arrangement {
        val userConfigRepository: UserConfigRepository = mock(mode = MockMode.autoUnit)
        val slowSyncRepository: SlowSyncRepository = mock(mode = MockMode.autoUnit)

        fun withMeetingsEnabled(enabled: Boolean) = apply {
            everySuspend {
                userConfigRepository.isMeetingsEnabled()
            } returns enabled
        }

        fun withSetMeetingsEnabledReturning(result: Either<StorageFailure, Unit>) = apply {
            everySuspend {
                userConfigRepository.setMeetingsEnabled(any())
            } returns result
        }

        fun arrange() = this to MeetingsConfigHandler(userConfigRepository, slowSyncRepository)
    }
}
