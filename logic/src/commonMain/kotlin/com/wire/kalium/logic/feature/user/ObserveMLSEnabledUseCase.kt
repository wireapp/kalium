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

import com.wire.kalium.logic.configuration.MLSEnablingSetting
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn

/**
 * Observe [MLSEnablingSetting] to notify user when setting is changed
 */
interface ObserveMLSEnabledUseCase {
    /**
     * @return [Flow] of [MLSEnablingSetting]
     */
    operator fun invoke(): Flow<MLSEnablingSetting>
}

internal class ObserveMLSEnabledUseCaseImpl(
    private val userConfigRepository: UserConfigRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : ObserveMLSEnabledUseCase {

    override fun invoke(): Flow<MLSEnablingSetting> = userConfigRepository
        .observeIsMLSEnabled()
        .filter { mlsSetting ->
            mlsSetting.status && (mlsSetting.notifyUserAfter?.let { it <= DateTimeUtil.currentInstant() } ?: false)
        }
        .flowOn(dispatcher.io)

}
