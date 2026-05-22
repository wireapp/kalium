/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
import com.wire.kalium.logic.data.featureConfig.PreventAdminlessGroupsConfigModel
import com.wire.kalium.logic.data.featureConfig.Status

internal class PreventAdminlessGroupsConfigHandler(
    private val userConfigRepository: UserConfigRepository
) {
    internal suspend fun handle(model: PreventAdminlessGroupsConfigModel?): Either<CoreFailure, Unit> =
        when {
            model == null -> userConfigRepository.setPreventAdminlessGroupsEnabled(false)
            else -> userConfigRepository.setPreventAdminlessGroupsEnabled(model.status == Status.ENABLED)
        }
}
