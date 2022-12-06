package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.id.PlainId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.DelicateKaliumApi

@DelicateKaliumApi("This use case performs network calls, consider using IsMLSEnabledUseCase.")
interface IsAllowedToRegisterMLSClientUseCase {
    suspend operator fun invoke(): Boolean
}

@OptIn(DelicateKaliumApi::class)
class IsAllowedToRegisterMLSClientUseCaseImpl(
    private val featureSupport: FeatureSupport,
    private val featureConfigRepository: FeatureConfigRepository,
    private val selfUserId: UserId
) : IsAllowedToRegisterMLSClientUseCase {

    override suspend operator fun invoke(): Boolean =
        featureConfigRepository.getFeatureConfigs().fold({
            false
        }, {
            featureSupport.isMLSSupported &&
                    it.mlsModel.status == Status.ENABLED &&
                    it.mlsModel.allowedUsers.contains(PlainId(selfUserId.value))
        })
}
