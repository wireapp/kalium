package com.wire.kalium.network.api.featureConfigs

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.get


interface FeatureConfigApi {
    suspend fun fileSharingFeatureConfig(): NetworkResponse<FeatureConfigResponse>
}

class FeatureConfigApiImpl internal constructor(private val authenticatedNetworkClient: AuthenticatedNetworkClient) : FeatureConfigApi {
    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun fileSharingFeatureConfig(): NetworkResponse<FeatureConfigResponse> =
        wrapKaliumResponse { httpClient.get(FILE_SHARING) }


    companion object {
        const val FEATURE_CONFIG = "feature-config/"
        const val FILE_SHARING = "$FEATURE_CONFIG/fileSharing"
    }
}
