package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.network.api.featureConfigs.FeatureFlagStatusDTO

interface FeatureConfigEventReceiver : EventReceiver<Event.FeatureConfig>

class FeatureConfigEventReceiverImpl(
    private val userConfigRepository: UserConfigRepository,
    private val kaliumConfigs: KaliumConfigs
) : FeatureConfigEventReceiver {

    override suspend fun onEvent(event: Event.FeatureConfig) {
        handleFeatureConfigEvent(event)
    }

    private fun handleFeatureConfigEvent(event: Event.FeatureConfig) {
        when (event) {
            is Event.FeatureConfig.FileSharingUpdated -> {
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
            is Event.FeatureConfig.UnknownFeatureUpdated -> kaliumLogger.w("Ignoring unknown feature config update")
        }
    }
}
