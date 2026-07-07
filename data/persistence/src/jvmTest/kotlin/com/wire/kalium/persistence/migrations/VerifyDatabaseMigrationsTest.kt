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
package com.wire.kalium.persistence.migrations

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.async.coroutines.awaitMigrate
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wire.kalium.persistence.GlobalDatabase
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.migrations.dump.SchemaDump
import com.wire.kalium.persistence.migrations.dump.SqliteSchemaDumper
import dev.andrewbailey.diff.differenceOf
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class VerifyDatabaseMigrationsTest {

    @Test
    fun verifyUserDatabaseMigrations() = runTest {
        // given
        // clean up and prepare base schema for derivation
        File("build/user-schema-dumps").deleteRecursively()
        File("src/commonMain/db_user/schemas/34.db")
            .copyTo(File(DatabaseSchemaSource.UserDatabaseDefaults.derivedSchemaDbPath), overwrite = true)

        val (_, dumper) = Arrangement(DatabaseSchemaSource.UserDatabaseDefaults)
            .withFreshDatabaseSchemaFromDefinitions()
            .withDerivedUserDatabaseFromMigrations()
            .arrange()

        val (freshDbDumper, derivedDbDumper) = dumper
        val freshDBSqlDump = freshDbDumper.dumpToJson()
        val derivedDBSqlDump = derivedDbDumper.dumpToJson()

        // when
        val result = differenceOf(original = freshDBSqlDump.allComponents, updated = derivedDBSqlDump.allComponents)

        // then
        assertValidUserSchemaDump(freshDBSqlDump)
        assertValidUserSchemaDump(derivedDBSqlDump)
        assertEquals(
            expected = true,
            actual = result.operations.isEmpty(),
            message = "Database schema migration is not up to date." +
                    "Differences found:\n${result.operations.joinToString("\n")}"
        )
    }

    @Test
    fun givenChannelBeforeMigration138_whenMigrating_thenBackfillsGroupType() = runTest {
        // given
        val dbFile = File("build/user-schema-dumps/migration-138-backfill.db")
        dbFile.parentFile.mkdirs()
        File("src/commonMain/db_user/schemas/34.db").copyTo(dbFile, overwrite = true)

        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")

        try {
            UserDatabase.Companion.Schema.awaitMigrate(driver, 34L, MIGRATION_138_START_VERSION)
            driver.execute(
                null,
                """
                    INSERT INTO Conversation (
                        qualified_id,
                        name,
                        type,
                        is_channel,
                        mls_group_state,
                        protocol,
                        muted_status,
                        muted_time,
                        creator_id,
                        last_modified_date,
                        access_list,
                        access_role_list,
                        last_read_date,
                        mls_last_keying_material_update_date,
                        mls_cipher_suite,
                        receipt_mode,
                        incomplete_metadata,
                        archived,
                        history_sharing_retention_seconds
                    )
                    VALUES
                        (
                            'regular@wire.com',
                            'Regular group',
                            'GROUP',
                            0,
                            'ESTABLISHED',
                            'PROTEUS',
                            'ALL_ALLOWED',
                            0,
                            'creator',
                            0,
                            '[]',
                            '[]',
                            0,
                            0,
                            'MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519',
                            'DISABLED',
                            0,
                            0,
                            0
                        ),
                        (
                            'channel@wire.com',
                            'Channel',
                            'GROUP',
                            1,
                            'ESTABLISHED',
                            'PROTEUS',
                            'ALL_ALLOWED',
                            0,
                            'creator',
                            0,
                            '[]',
                            '[]',
                            0,
                            0,
                            'MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519',
                            'DISABLED',
                            0,
                            0,
                            0
                        )
                """.trimIndent(),
                0
            )

            // when
            UserDatabase.Companion.Schema.awaitMigrate(driver, MIGRATION_138_START_VERSION, UserDatabase.Companion.Schema.version)

            // then
            assertEquals(
                mapOf(
                    "channel@wire.com" to "CHANNEL",
                    "regular@wire.com" to "GROUP"
                ),
                driver.selectConversationGroupTypes()
            )
            assertEquals(false, driver.conversationTableHasColumn("is_channel"))
        } finally {
            driver.close()
            dbFile.delete()
        }
    }

    @Test
    fun verifyGlobalDatabaseMigrations() = runTest {
        // given
        // clean up and prepare base schema for derivation
        File("build/global-schema-dumps").deleteRecursively()
        File("src/commonMain/db_global/schemas/5.db")
            .copyTo(File(DatabaseSchemaSource.GlobalDatabaseDefaults.derivedSchemaDbPath), overwrite = true)

        val (_, dumper) = Arrangement(DatabaseSchemaSource.GlobalDatabaseDefaults)
            .withFreshDatabaseSchemaFromDefinitions()
            .withDerivedUserDatabaseFromMigrations()
            .arrange()

        val (freshDbDumper, derivedDbDumper) = dumper
        val freshDBSqlDump = freshDbDumper.dumpToJson()
        val derivedDBSqlDump = derivedDbDumper.dumpToJson()

        // when
        val result = differenceOf(original = freshDBSqlDump.allComponents, updated = derivedDBSqlDump.allComponents)

        // then
        assertValidGlobalSchemaDump(freshDBSqlDump)
        assertValidGlobalSchemaDump(derivedDBSqlDump)
        assertEquals(
            expected = true,
            actual = result.operations.isEmpty(),
            message = "Database schema migration is not up to date." +
                    "Differences found:\n${result.operations.joinToString("\n")}"
        )
    }

    /**
     * Asserts that the global schema dump is valid (not empty) for tables.
     * Since as of today 2025, global database only has tables.
     */
    private fun assertValidGlobalSchemaDump(schemaDump: SchemaDump) {
        assertEquals(true, schemaDump.tables.isNotEmpty(), "Invalid schema dump: tables are empty")
    }

    /**
     * Asserts that the user schema dump is valid (not empty) for all components.
     * Since as of today 2025, user database has tables, views, indexes and triggers.
     */
    private fun assertValidUserSchemaDump(schemaDump: SchemaDump) {
        assertEquals(true, schemaDump.tables.isNotEmpty(), "Invalid schema dump: tables are empty")
        assertEquals(true, schemaDump.views.isNotEmpty(), "Invalid schema dump: views are empty")
        assertEquals(true, schemaDump.indexes.isNotEmpty(), "Invalid schema dump: indexes are empty")
        assertEquals(true, schemaDump.triggers.isNotEmpty(), "Invalid schema dump: triggers are empty")
    }

    private fun JdbcSqliteDriver.selectConversationGroupTypes(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        executeQuery(
            null,
            "SELECT qualified_id, group_type FROM Conversation ORDER BY qualified_id",
            mapper = { cursor ->
                while (cursor.next().value) {
                    result[cursor.getString(0)!!] = cursor.getString(1)!!
                }
                QueryResult.Unit
            },
            0
        )
        return result
    }

    private fun JdbcSqliteDriver.conversationTableHasColumn(columnName: String): Boolean {
        var hasColumn = false
        executeQuery(
            null,
            "PRAGMA table_info(Conversation)",
            mapper = { cursor ->
                while (cursor.next().value) {
                    hasColumn = hasColumn || cursor.getString(1) == columnName
                }
                QueryResult.Unit
            },
            0
        )
        return hasColumn
    }

    private class Arrangement(private val databaseSchemaSource: DatabaseSchemaSource) {

        suspend fun withFreshDatabaseSchemaFromDefinitions() = apply {
            val dbFile = File(databaseSchemaSource.freshSchemaDbPath)
            dbFile.parentFile.mkdirs()
            val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")

            when (databaseSchemaSource) {
                is DatabaseSchemaSource.UserDatabaseDefaults -> UserDatabase.Companion.Schema.create(driver).await()
                is DatabaseSchemaSource.GlobalDatabaseDefaults -> GlobalDatabase.Companion.Schema.create(driver).await()
            }
            driver.close()
            println("Fresh-schema DB created at: ${dbFile.absolutePath}")
        }

        suspend fun withDerivedUserDatabaseFromMigrations() = apply {
            val dbFile = File(databaseSchemaSource.derivedSchemaDbPath)
            dbFile.parentFile.mkdirs()
            val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")

            when (databaseSchemaSource) {
                is DatabaseSchemaSource.UserDatabaseDefaults -> UserDatabase.Companion.Schema.awaitMigrate(
                    driver,
                    34, // Starting from 34 since 33.sqm has errors.
                    UserDatabase.Companion.Schema.version
                )

                is DatabaseSchemaSource.GlobalDatabaseDefaults -> GlobalDatabase.Companion.Schema.awaitMigrate(
                    driver,
                    5, // Starting from 5 since 4.sqm has errors.
                    GlobalDatabase.Companion.Schema.version
                )
            }
            driver.close()
            println("Derived-from-migrations DB created at: ${dbFile.absolutePath}")
        }

        fun arrange() = this to Pair(
            SqliteSchemaDumper(
                dbPath = databaseSchemaSource.freshSchemaDbPath,
                outputPath = databaseSchemaSource.ddlFreshDumpJsonPath
            ),
            SqliteSchemaDumper(
                dbPath = databaseSchemaSource.derivedSchemaDbPath,
                outputPath = databaseSchemaSource.ddlDerivedDumpJsonPath
            )
        )
    }

    sealed class DatabaseSchemaSource(
        open val freshSchemaDbPath: String,
        open val derivedSchemaDbPath: String,
        open val ddlFreshDumpJsonPath: String,
        open val ddlDerivedDumpJsonPath: String
    ) {
        object UserDatabaseDefaults : DatabaseSchemaSource(
            freshSchemaDbPath = "build/user-schema-dumps/fresh-schema.db",
            derivedSchemaDbPath = "build/user-schema-dumps/derived-schema.db",
            ddlFreshDumpJsonPath = "build/user-schema-dumps/database-schema-from-definitions.json",
            ddlDerivedDumpJsonPath = "build/user-schema-dumps/database-schema-from-migrations.json"
        )

        object GlobalDatabaseDefaults : DatabaseSchemaSource(
            freshSchemaDbPath = "build/global-schema-dumps/fresh-schema.db",
            derivedSchemaDbPath = "build/global-schema-dumps/derived-schema.db",
            ddlFreshDumpJsonPath = "build/global-schema-dumps/database-schema-from-definitions.json",
            ddlDerivedDumpJsonPath = "build/global-schema-dumps/database-schema-from-migrations.json"
        )
    }

    private companion object {
        const val MIGRATION_138_START_VERSION = 138L
    }
}
