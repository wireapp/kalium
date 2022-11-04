package com.wire.kalium.logic.network

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.foldToEitherWhileRight
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
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
        metadataDAO.valueByKey(LAST_API_VERSION_IN_USE_KEY)?.toInt()?.let { previousApiVersion ->
            migrations.filter { it.first in (previousApiVersion + 1)..apiVersion }.foldToEitherWhileRight(Unit) { migration, _ ->
                kaliumLogger.i("Performing API migration for version: ${migration.first}")
                migration.second()
            }.onSuccess {
                persistCurrentApiVersion()
            }
        } ?: persistCurrentApiVersion()
    }

    private suspend fun persistCurrentApiVersion() {
        metadataDAO.insertValue(LAST_API_VERSION_IN_USE_KEY, apiVersion.toString())
    }

    companion object {
        const val LAST_API_VERSION_IN_USE_KEY = "lastApiVersionInUse"
    }
}
