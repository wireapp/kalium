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

package com.wire.kalium.logic.network

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.persistence.dao.MetadataDAO

interface ApiMigration {
    suspend operator fun invoke(): Either<CoreFailure, Unit>
}

/**
 * Manages a list migrations for each API version.
 */
class ApiMigrationManager(
    private val apiVersion: Int,
    private val metadataDAO: MetadataDAO,
    private val migrations: List<Pair<Int, ApiMigration>>
) {

    /**
     * Perform any necessary migrations if API version has increased since the
     * last time the application launched.
     */
    suspend fun performMigrations() {
        getLastUsedApiVersion()?.let { lastUsedApiVersion ->
            kaliumLogger.i("Migrating from API version $lastUsedApiVersion to $apiVersion")
            migrations.filter { it.first in (lastUsedApiVersion + 1)..apiVersion }.foldToEitherWhileRight(Unit) { migration, _ ->
                kaliumLogger.i("Performing API migration for version: ${migration.first}")
                migration.second()
            }.onSuccess {
                persistLastUsedApiVersion()
            }
        } ?: persistLastUsedApiVersion()
    }

    private suspend fun getLastUsedApiVersion(): Int? =
        wrapStorageRequest {
            metadataDAO.valueByKey(LAST_API_VERSION_IN_USE_KEY)
        }.getOrNull()?.toInt()

    private suspend fun persistLastUsedApiVersion() {
        wrapStorageRequest {
            metadataDAO.insertValue(apiVersion.toString(), LAST_API_VERSION_IN_USE_KEY)
        }.onFailure {
            kaliumLogger.d("Failed to persist API version: $it")
        }
    }

    companion object {
        const val LAST_API_VERSION_IN_USE_KEY = "lastApiVersionInUse"
    }
}
