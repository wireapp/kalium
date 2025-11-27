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
package com.wire.kalium.persistence.dao.migration

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest

/**
 * Base class for testing database migrations that involve both schema and content changes.
 *
 * This test framework allows you to:
 * 1. Load a pre-migration database schema from a .db file
 * 2. Insert test data into the old schema
 * 3. Run the migration SQL manually
 * 4. Verify the migrated data in the new schema
 *
 * Usage:
 * ```kotlin
 * class Migration119Test : SchemaMigrationTest() {
 *     @Test
 *     fun testMemberChangeMigration() = runMigrationTest(
 *         schemaVersion = 119,
 *         setupOldSchema = { driver ->
 *             // Insert data into old tables (MessageMemberChangeContent, etc.)
 *             driver.execute(...)
 *         },
 *         migrationSql = {
 *             // Copy the migration SQL from the .sqm file
 *             """
 *                 CREATE TABLE IF NOT EXISTS MessageSystemContent (...);
 *                 INSERT INTO MessageSystemContent ...;
 *                 DROP TABLE MessageMemberChangeContent;
 *             """
 *         },
 *         verifyNewSchema = { driver ->
 *             // Query and verify the new MessageSystemContent table
 *             val result = driver.executeQuery(...)
 *             assertEquals(expected, result)
 *         }
 *     )
 * }
 * ```
 */
abstract class SchemaMigrationTest {

    protected val dispatcher: TestDispatcher = StandardTestDispatcher()
    private var currentTestDbPath: String? = null

    /**
     * Runs a migration test with the specified schema version and test steps.
     *
     * @param schemaVersion The schema version to test (e.g., 119)
     * @param setupOldSchema Lambda to insert test data into the old schema
     * @param migrationSql Lambda that returns the migration SQL to execute
     * @param verifyNewSchema Lambda to verify the migrated data
     */
    protected fun runMigrationTest(
        schemaVersion: Int,
        setupOldSchema: (SqlDriver) -> Unit,
        migrationSql: () -> String,
        verifyNewSchema: (SqlDriver) -> Unit
    ) {
        // Step 1: Copy the schema file to a temporary location
        val driver = createDriverFromSchemaFile(schemaVersion)
        currentTestDbPath = (driver as JdbcSqliteDriver).toString()

        try {
            // Step 2: Setup test data in the old schema
            setupOldSchema(driver)

            // Step 3: Execute the migration SQL
            val migration = migrationSql()
            executeMigration(driver, migration)

            // Step 4: Verify the new schema and data
            verifyNewSchema(driver)
        } finally {
            driver.close()
        }
    }

    /**
     * Creates a SqlDriver from a pre-existing schema file.
     * The schema file should be located at: src/commonTest/kotlin/com/wire/kalium/persistence/schemas/{version}.db
     */
    private fun createDriverFromSchemaFile(schemaVersion: Int): SqlDriver {
        // Try to find the schema file as a resource first
        val schemaResourcePath = "src/commonTest/kotlin/com/wire/kalium/persistence/schemas/$schemaVersion.db"
        val schemaFile: File = File(schemaResourcePath)
        if (!schemaFile.exists()) {
            throw IllegalStateException("Schema file not found: $schemaResourcePath")
        }
        val schemaStream = schemaFile.inputStream()

        // Create a temporary file to work with
        val tempDbFile = Files.createTempFile("migration-test-$schemaVersion-", ".db").toFile()
        tempDbFile.deleteOnExit()

        // Copy the schema file to the temporary location
        schemaStream.use { input ->
            tempDbFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        // Create a JDBC SQLite driver pointing to the temporary database
        val driver = JdbcSqliteDriver("jdbc:sqlite:${tempDbFile.absolutePath}")

        // Enable foreign keys for accurate migration testing
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)

        return driver
    }

    /**
     * Executes the migration SQL, handling multi-statement scripts.
     */
    private fun executeMigration(driver: SqlDriver, migrationSql: String) {
        // Split the migration SQL by semicolons and execute each statement
        // This is necessary because SQLite JDBC driver doesn't handle multi-statement scripts well
        val statements = migrationSql.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        for (statement in statements) {
            try {
                driver.execute(null, statement, 0)
            } catch (e: Exception) {
                throw IllegalStateException("Failed to execute migration statement:\n$statement", e)
            }
        }
    }

    /**
     * Helper function to execute a query and return results as a list of maps.
     * Each map represents a row with column names as keys.
     */
    protected fun SqlDriver.executeQueryAsList(
        sql: String,
        parameters: Int = 0,
        binders: (app.cash.sqldelight.db.SqlPreparedStatement.() -> Unit)? = null
    ): List<Map<String, Any?>> {
        return executeQuery(null, sql, mapper = { cursor ->
            val results = mutableListOf<Map<String, Any?>>()
            val columnCount = cursor.next().value?.let {
                // Get column count from the cursor
                var count = 0
                while (true) {
                    try {
                        cursor.getString(count)
                        count++
                    } catch (e: Exception) {
                        break
                    }
                }
                count
            } ?: 0

            if (columnCount > 0) {
                do {
                    val row = mutableMapOf<String, Any?>()
                    for (i in 0 until columnCount) {
                        // Try to get the value as different types
                        val value: Any? = try {
                            cursor.getString(i)
                        } catch (e: Exception) {
                            try {
                                cursor.getLong(i)
                            } catch (e: Exception) {
                                try {
                                    cursor.getDouble(i)
                                } catch (e: Exception) {
                                    try {
                                        cursor.getBytes(i)
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                            }
                        }
                        // Use index as column name for simplicity
                        row["col_$i"] = value
                    }
                    results.add(row)
                } while (cursor.next().value)
            }

            app.cash.sqldelight.db.QueryResult.Value(results)
        }, parameters, binders).value
    }

    /**
     * Helper function to insert data using a SQL INSERT statement.
     */
    protected fun SqlDriver.executeInsert(sql: String) {
        execute(null, sql, 0)
    }

    /**
     * Helper function to execute a SELECT query and get a single result.
     */
    protected fun <T> SqlDriver.querySingleValue(
        sql: String,
        mapper: (app.cash.sqldelight.db.SqlCursor) -> T
    ): T? {
        return executeQuery(null, sql, mapper = { cursor ->
            val result: T? = if (cursor.next().value == true) {
                mapper(cursor)
            } else {
                null
            }
            app.cash.sqldelight.db.QueryResult.Value(result)
        }, 0).value
    }

    /**
     * Helper function to count rows in a table.
     */
    protected fun SqlDriver.countRows(tableName: String): Long {
        return querySingleValue("SELECT COUNT(*) FROM $tableName") { cursor ->
            cursor.getLong(0) ?: 0L
        } ?: 0L
    }

    @AfterTest
    fun cleanup() {
        currentTestDbPath?.let { path ->
            try {
                File(path).delete()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }
}
