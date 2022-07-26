package com.wire.kalium.network.api.featureConfigs

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.get

interface FeatureConfigApi {
    suspend fun featureConfigs(): NetworkResponse<FeatureConfigResponse>
}

class FeatureConfigApiImpl internal constructor(private val authenticatedNetworkClient: AuthenticatedNetworkClient) : FeatureConfigApi {
    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun featureConfigs(): NetworkResponse<FeatureConfigResponse> =
        wrapKaliumResponse { httpClient.get(FEATURE_CONFIG) }

    companion object {
        const val FEATURE_CONFIG = "feature-configs"
    }
}
