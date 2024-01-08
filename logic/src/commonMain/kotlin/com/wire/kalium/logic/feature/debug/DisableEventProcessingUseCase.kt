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
package com.wire.kalium.logic.feature.debug

import com.wire.kalium.logic.sync.incremental.EventProcessor

/**
 * Disables processing of incoming events but still mark them as processed.
 *
 * This use case useful for testing error scenarios where messages have been lost,
 * putting the client in an inconsistent state with the backend.
 */
interface DisableEventProcessingUseCase {
    suspend operator fun invoke(disabled: Boolean)
}

internal class DisableEventProcessingUseCaseImpl(
    private val eventProcessor: EventProcessor
) : DisableEventProcessingUseCase {

    override suspend fun invoke(disabled: Boolean) {
        eventProcessor.disableEventProcessing = disabled
    }
}
