/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.configuration

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.featureConfig.ChannelFeatureConfiguration
import com.wire.kalium.persistence.dao.MetadataDAO
import kotlinx.coroutines.flow.Flow

internal interface ChannelsConfigurationStorage {
    fun observePersistedChannelsConfiguration(): Flow<ChannelFeatureConfiguration?>
    suspend fun persistChannelsConfiguration(config: ChannelFeatureConfiguration): Either<StorageFailure, Unit>
}

@Suppress("FunctionNaming")
internal fun ChannelsConfigurationStorage(metadataDao: MetadataDAO) = object : ChannelsConfigurationStorage {
    private val channelConfigurationKey = "channel-config"

    override fun observePersistedChannelsConfiguration() = metadataDao.observeSerializable(
        channelConfigurationKey,
        ChannelFeatureConfiguration.serializer()
    )

    override suspend fun persistChannelsConfiguration(
        config: ChannelFeatureConfiguration
    ): Either<StorageFailure, Unit> = wrapStorageRequest {
        metadataDao.putSerializable(channelConfigurationKey, config, ChannelFeatureConfiguration.serializer())
    }

}
