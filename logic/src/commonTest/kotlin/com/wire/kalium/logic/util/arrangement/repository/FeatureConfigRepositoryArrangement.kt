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
package com.wire.kalium.logic.util.arrangement.repository

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.featureConfig.FeatureConfigModel
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.common.functional.Either
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.mock

interface FeatureConfigRepositoryArrangement {

    val featureConfigRepository: FeatureConfigRepository

    suspend fun withGetFeatureConfigsReturning(result: Either<NetworkFailure, FeatureConfigModel>)
}

class FeatureConfigRepositoryArrangementImpl : FeatureConfigRepositoryArrangement {
    @Mock
    override val featureConfigRepository: FeatureConfigRepository = mock(FeatureConfigRepository::class)

    override suspend fun withGetFeatureConfigsReturning(result: Either<NetworkFailure, FeatureConfigModel>) {
        coEvery {
            featureConfigRepository.getFeatureConfigs()
        }.returns(result)
    }
}



