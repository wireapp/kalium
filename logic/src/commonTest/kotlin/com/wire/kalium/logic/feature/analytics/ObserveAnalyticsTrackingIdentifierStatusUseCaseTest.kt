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

import app.cash.turbine.test
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.analytics.AnalyticsIdentifierResult
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangementImpl
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class ObserveAnalyticsTrackingIdentifierStatusUseCaseTest {

    @Test
    fun givenTrackingIdentifierExists_whenObservingTrackingIdentifier_thenReturnExistingIdentifier() = runTest {
        // given
        val (_, useCase) = Arrangement().arrange {
            withObserveTrackingIdentifier(Either.Right(CURRENT_IDENTIFIER))
            withGetPreviousTrackingIdentifier(null)
        }

        // when
        useCase().test {
            // then
            val item = awaitItem()
            assertIs<AnalyticsIdentifierResult.ExistingIdentifier>(item)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenPreviousTrackingIdentifierExists_whenObservingTrackingIdentifier_thenReturnMigrationIdentifier() = runTest {
        // given
        val (_, useCase) = Arrangement().arrange {
            withObserveTrackingIdentifier(Either.Right(CURRENT_IDENTIFIER))
            withGetPreviousTrackingIdentifier(PREVIOUS_IDENTIFIER)
        }

        // when
        useCase().test {
            // then
            val item = awaitItem()
            assertIs<AnalyticsIdentifierResult.MigrationIdentifier>(item)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenThereIsNoIdentifier_whenObservingTrackingIdentifier_thenReturnNonExistingIdentifier() = runTest {
        // given
        val (_, useCase) = Arrangement().arrange {
            withGetTrackingIdentifier(null)
            withObserveTrackingIdentifier(Either.Left(StorageFailure.DataNotFound))
        }

        // when
        useCase().test {
            // then
            val item = awaitItem()
            assertIs<AnalyticsIdentifierResult.NonExistingIdentifier>(item)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenExistingIdentifier_whenObservingTrackingIdentifierReturnsDataNotFound_thenReturnExistingIdentifier() = runTest {
        // given
        val (_, useCase) = Arrangement().arrange {
            withGetTrackingIdentifier(CURRENT_IDENTIFIER)
            withObserveTrackingIdentifier(Either.Left(StorageFailure.DataNotFound))
        }

        // when
        useCase().test {
            // then
            val item = awaitItem()
            assertIs<AnalyticsIdentifierResult.ExistingIdentifier>(item)

            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        const val PREVIOUS_IDENTIFIER = "abcd-1234"
        const val CURRENT_IDENTIFIER = "efgh-5678"
    }

    private class Arrangement : UserConfigRepositoryArrangement by UserConfigRepositoryArrangementImpl() {

        private val useCase: ObserveAnalyticsTrackingIdentifierStatusUseCase = ObserveAnalyticsTrackingIdentifierStatusUseCase(
            userConfigRepository = userConfigRepository
        )

        fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, ObserveAnalyticsTrackingIdentifierStatusUseCase> {
            runBlocking { block() }
            return this to useCase
        }
    }
}
