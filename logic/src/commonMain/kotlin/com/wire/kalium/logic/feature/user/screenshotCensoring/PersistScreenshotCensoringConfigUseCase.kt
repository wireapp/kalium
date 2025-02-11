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

package com.wire.kalium.logic.feature.user.screenshotCensoring

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.LOCAL_STORAGE
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.kaliumLogger

/**
 * UseCase that allow us to persist the configuration of screenshot censoring to enabled or not
 */
interface PersistScreenshotCensoringConfigUseCase {
    suspend operator fun invoke(enabled: Boolean): PersistScreenshotCensoringConfigResult
}

internal class PersistScreenshotCensoringConfigUseCaseImpl(
    private val userConfigRepository: UserConfigRepository,
) : PersistScreenshotCensoringConfigUseCase {

    private val logger by lazy { kaliumLogger.withFeatureId(LOCAL_STORAGE) }

    override suspend fun invoke(enabled: Boolean): PersistScreenshotCensoringConfigResult =
        userConfigRepository.setScreenshotCensoringConfig(enabled)
            .fold({
                logger.e("Failed trying to update screenshot censoring configuration")
                PersistScreenshotCensoringConfigResult.Failure(it)
            }) {
                PersistScreenshotCensoringConfigResult.Success
            }
}

sealed class PersistScreenshotCensoringConfigResult {
    data object Success : PersistScreenshotCensoringConfigResult()
    data class Failure(val cause: CoreFailure) : PersistScreenshotCensoringConfigResult()
}
