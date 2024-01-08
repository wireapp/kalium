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

package com.wire.kalium.logic.feature.server

import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.functional.onSuccess

/**
 * Iterates over all locally stored server configs and update each api version
 */
interface UpdateApiVersionsUseCase {
    suspend operator fun invoke()
}

class UpdateApiVersionsUseCaseImpl internal constructor(
    private val configRepository: ServerConfigRepository
) : UpdateApiVersionsUseCase {
    override suspend operator fun invoke() {
        configRepository.configList().onSuccess { configList ->
            configList.forEach {
                configRepository.updateConfigApiVersion(it.id)
            }
        }
    }
}
