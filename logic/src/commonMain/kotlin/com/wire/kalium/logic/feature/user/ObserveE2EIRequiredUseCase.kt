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

import com.wire.kalium.logic.configuration.E2EISetting
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.getOrNull
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlin.time.Duration

/**
 * Observe [E2EISetting] to notify user when setting is changed to Enabled
 */
interface ObserveE2EIRequiredUseCase {
    /**
     * @return [Flow] of [MLSE2EIRequiredResult]
     */
    operator fun invoke(): Flow<MLSE2EIRequiredResult>
}

internal class ObserveE2EIRequiredUseCaseImpl(
    private val userConfigRepository: UserConfigRepository,
    private val dispatcher: CoroutineDispatcher = KaliumDispatcherImpl.io
) : ObserveE2EIRequiredUseCase {

    override fun invoke(): Flow<MLSE2EIRequiredResult> = userConfigRepository
        .observeIsE2EISetting()
        .map { it.getOrNull() }
        .filterNotNull()
        .filter { setting -> setting.isRequired && setting.gracePeriodEnd != null }
        .delayUntilNotifyTime()
        .map { setting ->
            if (setting.gracePeriodEnd!! <= DateTimeUtil.currentInstant())
                MLSE2EIRequiredResult.NoGracePeriod
            else MLSE2EIRequiredResult.WithGracePeriod(setting.gracePeriodEnd.minus(DateTimeUtil.currentInstant()))
        }
        .flowOn(dispatcher)
}

private fun Flow<E2EISetting>.delayUntilNotifyTime(): Flow<E2EISetting> = flatMapLatest { setting ->
    val delayMillis = setting.notifyUserAfter
        ?.minus(DateTimeUtil.currentInstant())
        ?.inWholeMilliseconds
        ?.coerceAtLeast(0L)
        ?: 0L
    flowOf(setting).onStart { delay(delayMillis) }
}

sealed class MLSE2EIRequiredResult {
    data class WithGracePeriod(val gracePeriod: Duration) : MLSE2EIRequiredResult()
    object NoGracePeriod : MLSE2EIRequiredResult()
}
