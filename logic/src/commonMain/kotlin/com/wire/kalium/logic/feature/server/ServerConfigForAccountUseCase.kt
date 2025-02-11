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
@file:Suppress("konsist.useCasesShouldNotAccessDaoLayerDirectly")

package com.wire.kalium.logic.feature.server

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigMapper
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAO

/**
 * Gets the server configuration for the given user.
 */
class ServerConfigForAccountUseCase internal constructor(
    private val dao: ServerConfigurationDAO,
    private val serverConfigMapper: ServerConfigMapper = MapperProvider.serverConfigMapper()
) {
    /**
     * @param userId the id of the user
     * @return the [ServerConfig] for the given user if successful, otherwise a [StorageFailure]
     */
    suspend operator fun invoke(userId: UserId): Result =
        wrapStorageRequest { dao.configForUser(userId.toDao()) }
            .map { serverConfigMapper.fromEntity(it) }
            .fold(Result::Failure, Result::Success)

    sealed class Result {
        data class Success(val config: ServerConfig) : Result()
        data class Failure(val cause: StorageFailure) : Result()
    }
}
