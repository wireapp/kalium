package com.wire.kalium.logic.sync

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.featureConfig.FeatureConfigName
import com.wire.kalium.logic.data.featureConfig.FeatureConfigStatus
import com.wire.kalium.logic.featureFlags.KaliumConfigs

interface FeatureConfigEventReceiver : EventReceiver<Event.FeatureConfig>

class FeatureConfigEventReceiverImpl(
    private val userConfigRepository: UserConfigRepository,
    private val kaliumConfigs: KaliumConfigs
) : FeatureConfigEventReceiver {

    override suspend fun onEvent(event: Event.FeatureConfig) {
        when (event) {
            is Event.FeatureConfig.FeatureConfigUpdated -> handleFeatureConfigEvent(event)
        }
    }

    private fun handleFeatureConfigEvent(event: Event.FeatureConfig.FeatureConfigUpdated) {
        when (event.name) {
            FeatureConfigName.fileSharing -> {
                if (kaliumConfigs.fileRestrictionEnabled) {
                    userConfigRepository.setFileSharingStatus(false, null)
                } else {
                    when (event.status) {
                        FeatureConfigStatus.enabled -> userConfigRepository.setFileSharingStatus(status = true, isStatusChanged = true)
                        FeatureConfigStatus.disabled -> userConfigRepository.setFileSharingStatus(status = false, isStatusChanged = true)
                    }
                }
            }
        }
    }
}
