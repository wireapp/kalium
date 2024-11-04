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

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.functional.fold
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.zip

/**
 * Returns [true] only when our conference calling setting changes from false to true, meaning our conference calling
 * capability has been enabled. Internally we rely on getting event [Event.FeatureConfig.ConferenceCallingUpdated].
 * This can be used to inform user about the change, for example displaying a dialog about upgrading to enterprise edition.
 */
interface HasConferenceCallingActivatedUseCase {
    suspend operator fun invoke(): Flow<Any>
}

internal class HasConferenceCallingActivatedUseCaseImpl(
    private val userConfigRepository: UserConfigRepository,
) : HasConferenceCallingActivatedUseCase {
    override suspend fun invoke(): Flow<Any> {
        val enabledFlow = userConfigRepository.isConferenceCallingEnabledFlow()
            .map { isEnabled -> isEnabled.fold({ false }, { it }) }
        return enabledFlow
            .zip(enabledFlow.drop(1)) { old, new -> old to new }
            .filter { (old, new) -> !old && new }
            .distinctUntilChanged()
    }
}
