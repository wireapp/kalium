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

package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.featureFlags.KaliumConfigs
import io.mockative.Mockable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

@Mockable
interface UpdateKeyingMaterialThresholdProvider {

    val keyingMaterialUpdateThreshold: Duration
}

class UpdateKeyingMaterialThresholdProviderImpl(
    private val kaliumConfigs: KaliumConfigs
) : UpdateKeyingMaterialThresholdProvider {

    override val keyingMaterialUpdateThreshold: Duration
        get() = if (kaliumConfigs.lowerKeyingMaterialsUpdateThreshold)
            KEYING_MATERIAL_UPDATE_THRESHOLD_LOW
        else
            KEYING_MATERIAL_UPDATE_THRESHOLD

    companion object {
        // TODO: there are some edge cases and optimisations points to consider for M5-> please see: https://wearezeta.atlassian.net/browse/AR-1633
        internal val KEYING_MATERIAL_UPDATE_THRESHOLD = 90.days
        internal val KEYING_MATERIAL_UPDATE_THRESHOLD_LOW = 1.minutes
    }
}
