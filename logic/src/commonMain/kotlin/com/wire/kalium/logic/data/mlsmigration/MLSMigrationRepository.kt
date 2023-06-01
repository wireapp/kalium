/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
package com.wire.kalium.logic.data.mlsmigration

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.featureConfig.FeatureConfigMapper
import com.wire.kalium.logic.data.featureConfig.MLSMigrationModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigApi
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigData
import com.wire.kalium.persistence.dao.MetadataDAO
import kotlinx.datetime.Instant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface MLSMigrationRepository {
    suspend fun fetchMigrationConfiguration(): Either<CoreFailure, Unit>
    suspend fun setMigrationConfiguration(configuration: MLSMigrationModel): Either<StorageFailure, Unit>
    suspend fun getMigrationConfiguration(): Either<StorageFailure, MLSMigrationModel>
}

internal class MLSMigrationRepositoryImpl(
    private val featureConfigApi: FeatureConfigApi,
    private val metadataDAO: MetadataDAO,
    private val featureConfigMapper: FeatureConfigMapper = MapperProvider.featureConfigMapper()
) : MLSMigrationRepository {

    @Suppress("MagicNumber")
    override suspend fun fetchMigrationConfiguration(): Either<CoreFailure, Unit> {
        return wrapApiRequest { featureConfigApi.featureConfigs() }
            .flatMap {
                it.mlsMigration?.let { mlsMigration ->
                    setMigrationConfiguration(featureConfigMapper.fromDTO(mlsMigration))
                } ?: run {
                    setMigrationConfiguration(
                        MLSMigrationModel( // TODO jacob debugging values while not implemented on BE
                            Instant.DISTANT_PAST,
                            Instant.DISTANT_PAST,
                            100,
                            100,
                            Status.ENABLED
                        )
                    )
                }
            }
    }

    override suspend fun getMigrationConfiguration(): Either<StorageFailure, MLSMigrationModel> =
        wrapStorageRequest {
            metadataDAO.valueByKey(MLS_MIGRATION_CONFIGURATION_KEY)
        }.map { encodedValue ->
            featureConfigMapper.fromDTO(Json.decodeFromString<FeatureConfigData.MLSMigration>(encodedValue))
        }

    override suspend fun setMigrationConfiguration(configuration: MLSMigrationModel): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            metadataDAO.insertValue(Json.encodeToString(featureConfigMapper.fromModel(configuration)), MLS_MIGRATION_CONFIGURATION_KEY)
        }

    companion object {
        internal const val MLS_MIGRATION_CONFIGURATION_KEY = "mlsMigrationConfiguration"
    }
}
