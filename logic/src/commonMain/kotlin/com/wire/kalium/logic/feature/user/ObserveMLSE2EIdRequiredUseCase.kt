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

import com.wire.kalium.logic.configuration.MLSE2EIdSetting
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlin.time.Duration

/**
 * Observe [MLSE2EIdSetting] to notify user when setting is changed to Enabled
 */
interface ObserveMLSE2EIdRequiredUseCase {
    /**
     * @return [Flow] of [MLSE2EIdRequiredResult]
     */
    operator fun invoke(): Flow<MLSE2EIdRequiredResult>
}

internal class ObserveMLSE2EIdRequiredUseCaseImpl(
    private val userConfigRepository: UserConfigRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : ObserveMLSE2EIdRequiredUseCase {

    override fun invoke(): Flow<MLSE2EIdRequiredResult> = userConfigRepository
        .observeIsMLSE2EIdSetting()
        // TODO implement re-checking for the case when notifyUserAfter is in a future (some delayed flow)
        .filter { mlsSetting ->
            mlsSetting.status
                    && mlsSetting.enablingDeadline != null
                    && (mlsSetting.notifyUserAfter?.let { it <= DateTimeUtil.currentInstant() } ?: false)
        }
        .map { setting ->
            if (setting.enablingDeadline!! <= DateTimeUtil.currentInstant())
                MLSE2EIdRequiredResult.NoGracePeriod
            else MLSE2EIdRequiredResult.WithGracePeriod(setting.enablingDeadline.minus(DateTimeUtil.currentInstant()))
        }
        .flowOn(dispatcher.io)

}

sealed class MLSE2EIdRequiredResult {
    data class WithGracePeriod(val timeLeft: Duration) : MLSE2EIdRequiredResult()
    object NoGracePeriod : MLSE2EIdRequiredResult()
}
