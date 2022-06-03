package com.wire.kalium.logic.data.featureConfig

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.featureConfigs.FeatureConfigApi

interface FeatureConfigRepository {
    suspend fun getFileSharingFeatureConfig(): Either<NetworkFailure, FileSharingModel>
}

class FeatureConfigDataSource(
    private val featureConfigApi: FeatureConfigApi, private val featureConfigMapper: FeatureConfigMapper
) : FeatureConfigRepository {

    override suspend fun getFileSharingFeatureConfig(): Either<NetworkFailure, FileSharingModel> = wrapApiRequest {
        featureConfigApi.fileSharingFeatureConfig()
    }.map { featureConfigResponse ->
        featureConfigMapper.fromFileSharingResponse(featureConfigResponse)
    }
}
