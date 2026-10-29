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
package com.wire.kalium.persistence.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.wrapConnection
import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.DatabaseConnection
import co.touchlab.sqliter.DatabaseManager
import co.touchlab.sqliter.createDatabaseManager
import com.wire.kalium.persistence.util.FileNameUtil
import com.wire.kalium.logger.AppleSuspensionDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import co.touchlab.sqliter.JournalMode
import co.touchlab.sqliter.interop.Logger
import kotlinx.cinterop.toKString
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fputs
import platform.posix.getenv

actual data class PlatformDatabaseData(
    val storageData: StorageData,
    val useGradleSafeSqliterLogging: Boolean = false,
    val accountResourceAccess: com.wire.kalium.util.AccountResourceAccess? = null
)

sealed class StorageData {
    data class FileBacked(val storePath: String) : StorageData()
    data object InMemory : StorageData()
}

/**
 * @param passphrase SQLCipher's raw-key form, `x'<64 hex digits>'`, which encrypts the database, or on iOS the raw-key
 * form with the salt, which encrypts it behind a readable header, see [requireSqlCipherKey] and [setSqlCipherKey]. Null
 * or empty leaves the database unencrypted; the backup export uses an empty one for its plain copy.
 */
fun databaseDriver(
    driverUri: String?,
    dbName: String,
    schema: SqlSchema<QueryResult.Value<Unit>>,
    passphrase: ByteArray? = null,
    config: DriverConfigurationBuilder.() -> Unit = {}
): SqlDriver {
    val databaseKind = if (dbName == FileNameUtil.globalDBName()) "global_database" else "user_database"
    val driverConfiguration = DriverConfigurationBuilder().apply(config)
    val sqlCipherKey = passphrase?.takeIf { it.isNotEmpty() }?.decodeToString()?.also(::requireSqlCipherKey)
    val inMemory = driverUri == null
    val configuration = DatabaseConfiguration(
        name = dbName,
        version = schema.version.toInt(),
        journalMode = if (driverConfiguration.isWALEnabled) JournalMode.WAL else JournalMode.DELETE,
        inMemory = inMemory,
        loggingConfig = if (driverConfiguration.useGradleSafeSqliterLogging) {
            DatabaseConfiguration.Logging(logger = GradleSafeSqliterLogger)
        } else {
            DatabaseConfiguration.Logging()
        },
        create = { connection ->
            wrapConnection(connection) { schema.create(AppleSuspensionSqlDriver(it, databaseKind)) }
        },
        upgrade = { connection, oldVersion, newVersion ->
            wrapConnection(connection) {
                schema.migrate(AppleSuspensionSqlDriver(it, databaseKind), oldVersion.toLong(), newVersion.toLong())
            }
        }
    ).copy(
        extendedConfig = if (inMemory) DatabaseConfiguration.Extended(
            foreignKeyConstraints = driverConfiguration.areForeignKeyConstraintsEnforced
        ) else DatabaseConfiguration.Extended(
            basePath = driverUri,
            foreignKeyConstraints = driverConfiguration.areForeignKeyConstraintsEnforced
        ),
        lifecycleConfig = DatabaseConfiguration.Lifecycle(
            // Every connection, the readers of a WAL database included, gets the key before SQLiter reads the file.
            onCreateConnection = { connection -> sqlCipherKey?.let(connection::setSqlCipherKey) }
        )
    )
    return nativeDatabaseDriver(configuration, maxReaderConnections = NATIVE_MAX_READER_CONNECTIONS, databaseKind = databaseKind)
}

/** Track connection creation, including connections that fail before SQLDelight registers them. */
internal fun nativeDatabaseDriver(
    configuration: DatabaseConfiguration,
    maxReaderConnections: Int,
    databaseKind: String = "user_database",
): SqlDriver {
    val manager = createDatabaseManager(configuration)
    val failure = MutableStateFlow<Throwable?>(null)
    val connections = MutableStateFlow<List<DatabaseConnection>>(emptyList())
    val trackedManager = object : DatabaseManager by manager {
        override fun createMultiThreadedConnection(): DatabaseConnection = track { manager.createMultiThreadedConnection() }
        override fun createSingleThreadedConnection(): DatabaseConnection = track { manager.createSingleThreadedConnection() }

        @Suppress("TooGenericExceptionCaught")
        private fun track(create: () -> DatabaseConnection): DatabaseConnection {
            val token = AppleSuspensionDiagnostics.beginDatabaseOperation(databaseKind, "open")
            return try {
                create().also { connection ->
                    connections.update { it + connection }
                    AppleSuspensionDiagnostics.endDatabaseOperation(token, "success")
                }
            } catch (cause: Throwable) {
                AppleSuspensionDiagnostics.endDatabaseOperation(token, "failure")
                failure.compareAndSet(null, cause)
                throw cause
            }
        }
    }
    val driver = NativeSqliteDriver(trackedManager, maxReaderConnections)
    val trackedDriver = object : SqlDriver by driver {
        @Suppress("TooGenericExceptionCaught")
        override fun close() {
            try {
                driver.close()
                check(connections.value.all { it.closed }) { "A native connection did not reach its pool for cleanup" }
            } catch (cause: Throwable) {
                failure.compareAndSet(null, cause)
            }
            failure.value?.let { throw IllegalStateException("Native database cleanup cannot be established", it) }
        }
    }
    return AppleSuspensionSqlDriver(trackedDriver, databaseKind)
}

private object GradleSafeSqliterLogger : Logger {
    override fun trace(message: String) = Unit
    override val vActive: Boolean = false
    override fun vWrite(message: String) = Unit
    override val eActive: Boolean = true
    override fun eWrite(message: String, exception: Throwable?) {
        // Keep concise SQLiter error visibility without dumping stack traces that
        // can overwhelm Kotlin/Gradle native test output parsing.
        println(message)
        exception?.let {
            val trace = it.stackTraceToString()
            appendSqliterTraceToFile("$message\n$trace")
            if (isSqliterFullTracesEnabled()) {
                println(trace)
            }
        }
    }
}

private const val SQLITER_FULL_TRACES_ENV = "KALIUM_SQLITER_FULL_TRACES"
private const val SQLITER_TRACE_FILE_ENV = "KALIUM_SQLITER_TRACE_FILE"
private const val NATIVE_MAX_READER_CONNECTIONS = 32

private fun isSqliterFullTracesEnabled(): Boolean {
    val value = getenv(SQLITER_FULL_TRACES_ENV)?.toKString()?.lowercase() ?: return false
    return value == "true" || value == "1" || value == "yes"
}

private fun appendSqliterTraceToFile(content: String) {
    val path = getenv(SQLITER_TRACE_FILE_ENV)?.toKString()?.takeIf { it.isNotBlank() } ?: return
    val file = fopen(path, "a") ?: return
    try {
        fputs(content, file)
        fputs("\n\n", file)
    } finally {
        fclose(file)
    }
}
