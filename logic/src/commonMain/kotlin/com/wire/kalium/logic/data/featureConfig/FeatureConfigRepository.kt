/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.data.featureConfig

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigApi
import io.mockative.Mockable

@Mockable
interface FeatureConfigRepository {
    suspend fun getFeatureConfigs(): Either<NetworkFailure, FeatureConfigModel>
}

class FeatureConfigDataSource(
    private val featureConfigApi: FeatureConfigApi,
    private val featureConfigMapper: FeatureConfigMapper = MapperProvider.featureConfigMapper()
) : FeatureConfigRepository {

    override suspend fun getFeatureConfigs(): Either<NetworkFailure, FeatureConfigModel> = wrapApiRequest {
        featureConfigApi.featureConfigs()
    }.map { featureConfigResponse ->
        featureConfigMapper.fromDTO(featureConfigResponse)
    }
}
