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

import com.benasher44.uuid.uuid4
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMapRightWithEither
import com.wire.kalium.logic.functional.isRight
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Use case that allows observing if the analytics tracking identifier
 * changes, due to receiving a new identifier from another client
 * or when it's user's first interaction with analytics.
 */
interface ObserveAnalyticsTrackingIdentifierStatusUseCase {
    /**
     * Use case [ObserveAnalyticsTrackingIdentifierStatusUseCase] operation
     *
     * @return a [AnalyticsIdentifierResult]
     */
    suspend operator fun invoke(): Flow<AnalyticsIdentifierResult>
}

@Suppress("FunctionNaming")
internal fun ObserveAnalyticsTrackingIdentifierStatusUseCase(
    userConfigRepository: UserConfigRepository,
    defaultLogger: KaliumLogger = kaliumLogger,
) = object : ObserveAnalyticsTrackingIdentifierStatusUseCase {

    private val TAG = "ObserveAnalyticsTrackingIdentifierStatusUseCase"
    private val logger = defaultLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.ANALYTICS)

    override suspend fun invoke(): Flow<AnalyticsIdentifierResult> =
        userConfigRepository
            .observeTrackingIdentifier()
            .distinctUntilChanged()
            .flatMapRightWithEither { currentIdentifier: String ->
                val result =
                    userConfigRepository.getPreviousTrackingIdentifier()?.let {
                        logger.i("$TAG Updating Tracking Identifier with migration value.")
                        AnalyticsIdentifierResult.MigrationIdentifier(
                            identifier = currentIdentifier
                        )
                    } ?: AnalyticsIdentifierResult.ExistingIdentifier(
                        identifier = currentIdentifier
                    ).also {
                        logger.i("$TAG Updating Tracking Identifier with existing value.")
                    }

                flowOf(Either.Right(result))
            }.map {
                // it's needed, otherwise it will be detected as Flow<Any>
                if (it.isRight()) it.value as AnalyticsIdentifierResult
                else {
                    userConfigRepository.getTrackingIdentifier()?.let { currentIdentifier: String ->
                        logger.i("$TAG Updating Tracking Identifier with existing value.")
                        AnalyticsIdentifierResult.ExistingIdentifier(
                            identifier = currentIdentifier
                        )
                    } ?: uuid4().toString().let { trackingIdentifier: String ->
                        logger.i("$TAG Generating new Tracking Identifier value.")
                        userConfigRepository.setTrackingIdentifier(
                            identifier = trackingIdentifier
                        )

                        AnalyticsIdentifierResult.NonExistingIdentifier(
                            identifier = trackingIdentifier
                        )
                    }
                }
            }
}

sealed class AnalyticsIdentifierResult {

    data class NonExistingIdentifier(
        val identifier: String
    ) : AnalyticsIdentifierResult()

    data class ExistingIdentifier(
        val identifier: String
    ) : AnalyticsIdentifierResult()

    data class MigrationIdentifier(
        val identifier: String
    ) : AnalyticsIdentifierResult()

    data object None : AnalyticsIdentifierResult()
}
