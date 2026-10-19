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
package com.wire.kalium.logic.feature.featureConfig.handler

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.configuration.E2EISettings
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.E2EIModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.util.DateTimeUtil
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class E2EIConfigHandler(private val userConfigRepository: UserConfigRepository) {

    /** The app retains its settings-failure policy; NSE requires those writes before consuming an event. */
    suspend fun handle(e2eiConfig: E2EIModel, requireSettings: Boolean = false): Either<CoreFailure, Unit> {
        val settings = setSettingsIfNeeded(e2eiConfig, requireSettings)
        if (requireSettings && settings is Either.Left) return settings
        return userConfigRepository.setE2EINotificationTime(DateTimeUtil.currentInstant())
    }

    private suspend fun setSettingsIfNeeded(e2eiConfig: E2EIModel, requireSettings: Boolean): Either<CoreFailure, Unit> {
        val gracePeriodEnd = e2eiConfig.config.verificationExpirationSeconds
            .toDuration(DurationUnit.SECONDS)
            .let { DateTimeUtil.currentInstant().plus(it) }

        val stored = userConfigRepository.getE2EISettings()
        if (requireSettings && stored is Either.Left && stored.value != StorageFailure.DataNotFound) return stored
        val currentSettings = stored.getOrNull()

        val newSettings = E2EISettings(
            isRequired = e2eiConfig.status == Status.ENABLED,
            discoverUrl = e2eiConfig.config.discoverUrl,
            gracePeriodEnd = gracePeriodEnd,
            shouldUseProxy = e2eiConfig.config.shouldUseProxy,
            crlProxy = e2eiConfig.config.crlProxy
        )

        return if (currentSettings?.isRequired == newSettings.isRequired && currentSettings.discoverUrl == newSettings.discoverUrl) {
            // that settings were already handled,
            // no need to re-write as it will reset gracePeriod
            Either.Right(Unit)
        } else {
            userConfigRepository.setE2EISettings(newSettings)
        }
    }
}
