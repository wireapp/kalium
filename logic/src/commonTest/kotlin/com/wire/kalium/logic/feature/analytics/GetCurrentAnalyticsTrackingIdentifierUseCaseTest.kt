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
package com.wire.kalium.logic.feature.analytics

import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangementImpl
import io.mockative.coVerify
import io.mockative.once
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetCurrentAnalyticsTrackingIdentifierUseCaseTest {

    @Test
    fun givenCurrentAnalyticsTrackingId_whenGettingTrackingId_thenCurrentTrackingIdIsReturned() = runTest {
        // given
        val (arrangement, useCase) = Arrangement().arrange {
            withGetTrackingIdentifier(CURRENT_IDENTIFIER)
        }

        // when
        val result = useCase()

        // then
        assertEquals(CURRENT_IDENTIFIER, result)
        coVerify {
            arrangement.userConfigRepository.getCurrentTrackingIdentifier()
        }.wasInvoked(exactly = once)
    }

    private companion object {
        const val CURRENT_IDENTIFIER = "efgh-5678"
    }

    private class Arrangement : UserConfigRepositoryArrangement by UserConfigRepositoryArrangementImpl() {

        private val useCase: GetCurrentAnalyticsTrackingIdentifierUseCase = GetCurrentAnalyticsTrackingIdentifierUseCase(
            userConfigRepository = userConfigRepository
        )

        fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, GetCurrentAnalyticsTrackingIdentifierUseCase> {
            runBlocking { block() }
            return this to useCase
        }
    }
}
