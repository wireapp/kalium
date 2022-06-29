package com.wire.kalium.logic.sync

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.featureConfig.FeatureConfigName
import com.wire.kalium.logic.data.featureConfig.FeatureConfigStatus

interface FeatureConfigEventReceiver : EventReceiver<Event.FeatureConfig>

class FeatureConfigEventReceiverImpl(
    private val userConfigRepository: UserConfigRepository,

    ) : FeatureConfigEventReceiver {

    override suspend fun onEvent(event: Event.FeatureConfig) {
        when (event) {
            is Event.FeatureConfig.FeatureConfigUpdated -> handleFeatureConfigEvent(event)
        }
    }

    private fun handleFeatureConfigEvent(event: Event.FeatureConfig.FeatureConfigUpdated) {
        when (event.name) {
            FeatureConfigName.FILE_SHARING -> {
                when (event.status) {
                    FeatureConfigStatus.ENABLED -> userConfigRepository.setFileSharingStatus(status = true, isStatusChanged = true)
                    FeatureConfigStatus.DISABLED -> userConfigRepository.setFileSharingStatus(status = false, isStatusChanged = true)
                }
            }
        }

    }
}
