/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.configuration.WireCellsConfig
import com.wire.kalium.logic.data.featureConfig.CellsInternalModel
import com.wire.kalium.logic.data.featureConfig.CellsModel
import com.wire.kalium.logic.data.featureConfig.Status

internal class CellsConfigHandler(
    private val userConfigRepository: UserConfigRepository
) {
    internal suspend fun handle(model: CellsModel?): Either<CoreFailure, Unit> =
        when {
            model == null -> userConfigRepository.setCellsEnabled(false)
            else -> userConfigRepository.setCellsEnabled(model.status == Status.ENABLED)
        }

    internal suspend fun handle(model: CellsInternalModel?): Either<CoreFailure, Unit> =
        userConfigRepository.setWireCellsConfig(
            config = model?.let {
                WireCellsConfig(
                    backendUrl = it.config.backend?.url
                )
            }
        )
}
