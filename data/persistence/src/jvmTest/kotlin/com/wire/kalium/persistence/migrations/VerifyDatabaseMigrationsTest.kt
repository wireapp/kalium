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

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wire.kalium.persistence.GlobalDatabase
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.migrations.dump.SchemaDump
import com.wire.kalium.persistence.migrations.dump.SqliteSchemaDumper
import dev.andrewbailey.diff.differenceOf
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class VerifyDatabaseMigrationsTest {

    @Test
    fun verifyUserDatabaseMigrations() {
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
    fun verifyGlobalDatabaseMigrations() {
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

    private class Arrangement(private val databaseSchemaSource: DatabaseSchemaSource) {

        fun withFreshDatabaseSchemaFromDefinitions() = apply {
            val dbFile = File(databaseSchemaSource.freshSchemaDbPath)
            dbFile.parentFile.mkdirs()
            val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")

            when (databaseSchemaSource) {
                is DatabaseSchemaSource.UserDatabaseDefaults -> UserDatabase.Companion.Schema.create(driver)
                is DatabaseSchemaSource.GlobalDatabaseDefaults -> GlobalDatabase.Companion.Schema.create(driver)
            }
            driver.close()
            println("Fresh-schema DB created at: ${dbFile.absolutePath}")
        }

        fun withDerivedUserDatabaseFromMigrations() = apply {
            val dbFile = File(databaseSchemaSource.derivedSchemaDbPath)
            dbFile.parentFile.mkdirs()
            val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")

            when (databaseSchemaSource) {
                is DatabaseSchemaSource.UserDatabaseDefaults -> UserDatabase.Companion.Schema.migrate(
                    driver,
                    34, // Starting from 34 since 33.sqm has errors.
                    UserDatabase.Companion.Schema.version
                )

                is DatabaseSchemaSource.GlobalDatabaseDefaults -> GlobalDatabase.Companion.Schema.migrate(
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
}
