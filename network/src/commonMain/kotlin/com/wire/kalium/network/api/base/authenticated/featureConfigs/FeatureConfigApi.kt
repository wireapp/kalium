package com.wire.kalium.network.api.base.authenticated.featureConfigs

import com.wire.kalium.network.utils.NetworkResponse

interface FeatureConfigApi {
    suspend fun featureConfigs(): NetworkResponse<FeatureConfigResponse>
}
