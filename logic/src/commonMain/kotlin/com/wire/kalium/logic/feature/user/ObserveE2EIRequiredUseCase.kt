/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logic.configuration.E2EISettings
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.functional.getOrNull
import com.wire.kalium.logic.functional.onlyRight
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.Instant
import kotlin.time.Duration

/**
 * Observe [E2EISettings] to notify user when setting is changed to Enabled
 */
interface ObserveE2EIRequiredUseCase {
    /**
     * @return [Flow] of [E2EIRequiredResult]
     */
    suspend operator fun invoke(): Flow<E2EIRequiredResult>
}

internal class ObserveE2EIRequiredUseCaseImpl(
    private val userConfigRepository: UserConfigRepository,
    private val featureSupport: FeatureSupport,
    private val dispatcher: CoroutineDispatcher = KaliumDispatcherImpl.io
) : ObserveE2EIRequiredUseCase {

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun invoke(): Flow<E2EIRequiredResult> {
        if (!featureSupport.isMLSSupported) return flowOf(E2EIRequiredResult.NotRequired)

        return userConfigRepository
            .observeE2EINotificationTime()
            .map { it.getOrNull() }
            .filterNotNull()
            .delayUntilNotifyTime()
            .flatMapLatest {
                observeE2EISettings().flatMapLatest { setting ->
                    if (!setting.isRequired)
                        flowOf(E2EIRequiredResult.NotRequired)
                    else
                        observeCurrentE2EICertificate().map { currentCertificate ->
                            // TODO check here if current certificate needs to be renewed (soon, or now)

                            if (setting.gracePeriodEnd == null || setting.gracePeriodEnd <= DateTimeUtil.currentInstant())
                                E2EIRequiredResult.NoGracePeriod.Create
                            else E2EIRequiredResult.WithGracePeriod.Create(setting.gracePeriodEnd.minus(DateTimeUtil.currentInstant()))
                        }
                }
            }
            .flowOn(dispatcher)
    }

    private suspend fun observeE2EISettings() = userConfigRepository.observeE2EISettings().onlyRight().flowOn(dispatcher)

    private fun observeCurrentE2EICertificate(): Flow<Unit> {
        // TODO get current client E2EI certificate data here
        return flowOf(Unit).flowOn(dispatcher)
    }

    private fun Flow<Instant>.delayUntilNotifyTime(): Flow<Instant> = flatMapLatest { instant ->
        val delayMillis = instant
            .minus(DateTimeUtil.currentInstant())
            .inWholeMilliseconds
            .coerceAtLeast(NO_DELAY_MS)
        flowOf(instant).onStart { delay(delayMillis) }
    }

    companion object {
        private const val NO_DELAY_MS = 0L
    }
}

sealed class E2EIRequiredResult {
    sealed class WithGracePeriod(open val timeLeft: Duration) : E2EIRequiredResult() {
        data class Create(override val timeLeft: Duration) : WithGracePeriod(timeLeft)
        data class Renew(override val timeLeft: Duration) : WithGracePeriod(timeLeft)
    }

    sealed class NoGracePeriod : E2EIRequiredResult() {
        data object Create : NoGracePeriod()
        data object Renew : NoGracePeriod()
    }

    data object NotRequired : E2EIRequiredResult()
}
