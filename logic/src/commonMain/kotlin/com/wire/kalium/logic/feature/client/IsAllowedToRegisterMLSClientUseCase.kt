package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.id.PlainId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.DelicateKaliumApi
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Answers the question if the backend has MLS support enabled, is the self user allowed to register an MLS client?
 */
@DelicateKaliumApi("This use case performs network calls, consider using IsMLSEnabledUseCase.")
interface IsAllowedToRegisterMLSClientUseCase {
    suspend operator fun invoke(): Boolean
}

@OptIn(DelicateKaliumApi::class)
class IsAllowedToRegisterMLSClientUseCaseImpl(
    private val featureSupport: FeatureSupport,
    private val featureConfigRepository: FeatureConfigRepository,
    private val selfUserId: UserId,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : IsAllowedToRegisterMLSClientUseCase {

    override suspend operator fun invoke(): Boolean = withContext(dispatcher.default) {
        featureConfigRepository.getFeatureConfigs().fold({
            false
        }, {
            featureSupport.isMLSSupported &&
                    it.mlsModel.status == Status.ENABLED &&
                    it.mlsModel.allowedUsers.contains(PlainId(selfUserId.value))
        })
    }
}
