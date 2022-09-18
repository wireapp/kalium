package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.kaliumLogger

internal interface FeatureConfigEventReceiver : EventReceiver<Event.FeatureConfig>

internal class FeatureConfigEventReceiverImpl internal constructor(
    private val userConfigRepository: UserConfigRepository,
    private val userRepository: UserRepository,
    private val kaliumConfigs: KaliumConfigs,
    private val selfUserId: UserId
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
                    when (event.model.status) {
                        Status.ENABLED -> userConfigRepository.setFileSharingStatus(
                            status = true,
                            isStatusChanged = true
                        )

                        Status.DISABLED -> userConfigRepository.setFileSharingStatus(
                            status = false,
                            isStatusChanged = true
                        )
                    }
                }
            }

            is Event.FeatureConfig.MLSUpdated -> {
                val mlsEnabled = event.model.status == Status.ENABLED
                val selfUserIsWhitelisted = event.model.allowedUsers.contains(selfUserId.toPlainID())
                userConfigRepository.setMLSEnabled(mlsEnabled && selfUserIsWhitelisted)
            }

            is Event.FeatureConfig.ClassifiedDomainsUpdated -> {
                val classifiedDomainsEnabled = event.model.status == Status.ENABLED
                userConfigRepository.setClassifiedDomainsStatus(classifiedDomainsEnabled, event.model.config.domains)
            }

            is Event.FeatureConfig.UnknownFeatureUpdated -> kaliumLogger.w("Ignoring unknown feature config update")
        }
    }
}
