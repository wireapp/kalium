package com.wire.kalium.logic.data.featureConfig

import com.wire.kalium.network.api.featureConfigs.FeatureConfigResponse

class FeatureConfigMapper {
    fun fromFileSharingDTO(featureConfigResponse: FeatureConfigResponse): FileSharingModel =
        with(featureConfigResponse) { FileSharingModel(lockStatus = lockStatus, status = status) }
}
