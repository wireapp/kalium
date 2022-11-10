package com.wire.kalium.logic.network

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.foldToEitherWhileRight
import com.wire.kalium.logic.functional.getOrNull
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapStorageRequest
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
