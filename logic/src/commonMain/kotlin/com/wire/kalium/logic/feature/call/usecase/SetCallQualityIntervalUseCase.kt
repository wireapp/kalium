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

package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.common.logger.callingLogger
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * This use case is responsible for setting the network quality interval for a call to avs.
 */
public interface SetCallQualityIntervalUseCase {
    /**
     * @param intervalInSeconds the interval in seconds for how often the network quality should be updated.
     *                          0 means that the network quality updates are disabled.
     */
    public suspend operator fun invoke(intervalInSeconds: Int)
}

public class SetCallQualityIntervalUseCaseImpl internal constructor(
    private val callManager: Lazy<CallManager>,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : SetCallQualityIntervalUseCase {
    public override suspend operator fun invoke(intervalInSeconds: Int): Unit = withContext(dispatchers.io) {
        callingLogger.i("[SetCallQualityIntervalUseCase] Setting call network quality interval to: $intervalInSeconds seconds")
        callManager.value.setNetworkQualityInterval(intervalInSeconds)
    }
}
