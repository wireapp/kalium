package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.featureFlags.KaliumConfigs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

interface UpdateKeyingMaterialsThresholdProvider {

    val keyingMaterialUpdateThreshold: Duration
}

class UpdateKeyingMaterialsThresholdProviderImpl(
    private val kaliumConfigs: KaliumConfigs
) : UpdateKeyingMaterialsThresholdProvider {

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
