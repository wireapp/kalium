package com.wire.kalium.logic.sync

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.network.api.featureConfigs.FeatureFlagStatusDTO
import com.wire.kalium.network.api.notification.EventContentDTO

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
            EventContentDTO.FeatureConfig.FeatureConfigNameDTO.FILE_SHARING.name -> {
                if (kaliumConfigs.fileRestrictionEnabled) {
                    userConfigRepository.setFileSharingStatus(false, null)
                } else {
                    when (event.status) {
                        FeatureFlagStatusDTO.ENABLED.name -> userConfigRepository.setFileSharingStatus(
                            status = true,
                            isStatusChanged = true
                        )
                        FeatureFlagStatusDTO.DISABLED.name -> userConfigRepository.setFileSharingStatus(
                            status = false,
                            isStatusChanged = true
                        )
                    }
                }
            }
        }
    }
}
