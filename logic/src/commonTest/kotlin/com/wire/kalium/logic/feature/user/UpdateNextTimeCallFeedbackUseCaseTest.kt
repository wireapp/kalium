/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangementImpl
import com.wire.kalium.util.DateTimeUtil
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.matches
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class UpdateNextTimeCallFeedbackUseCaseTest {

    @Test
    fun givenNeverAskAgainIsTrue_whenInvoked_thenNextTimeSetToNegative() = runTest {
        val (arrangement, useCase) = Arrangement().arrange {
            withUpdateNextTimeForCallFeedback()
        }

        useCase(true)

        coVerify { arrangement.userConfigRepository.updateNextTimeForCallFeedback(eq(-1L)) }
            .wasInvoked()
    }

    @Test
    fun givenNeverAskAgainIsFalse_whenInvoked_thenNextTimeSetToNegative() = runTest {
        val (arrangement, useCase) = Arrangement().arrange {
            withUpdateNextTimeForCallFeedback()
        }
        val expectedMin = DateTimeUtil.currentInstant().plus(3.days).toEpochMilliseconds()
        val expectedMax = DateTimeUtil.currentInstant().plus(3.days).plus(10.minutes).toEpochMilliseconds()

        useCase(false)

        coVerify { arrangement.userConfigRepository.updateNextTimeForCallFeedback(matches { it in expectedMin..<expectedMax }) }
            .wasInvoked()
    }


    private class Arrangement : UserConfigRepositoryArrangement by UserConfigRepositoryArrangementImpl() {

        fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, UpdateNextTimeCallFeedbackUseCase> {
            runBlocking { block() }
            return this to UpdateNextTimeCallFeedbackUseCase(userConfigRepository)
        }
    }
}
