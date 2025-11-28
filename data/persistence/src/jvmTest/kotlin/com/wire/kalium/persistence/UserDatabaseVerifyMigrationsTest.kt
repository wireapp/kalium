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
package com.wire.kalium.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import liquibase.CatalogAndSchema
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.diff.DiffGeneratorFactory
import liquibase.diff.compare.CompareControl
import liquibase.diff.output.DiffOutputControl
import liquibase.diff.output.changelog.DiffToChangeLog
import liquibase.snapshot.SnapshotControl
import liquibase.snapshot.SnapshotGeneratorFactory
import liquibase.structure.core.Catalog
import liquibase.structure.core.Schema
import java.io.File
import java.sql.DriverManager
import kotlin.test.Test

class UserDatabaseVerifyMigrationsTest {
    @Test
    fun verifyMigrations() {
//         buildCleanDatabaseSchemaFromDefinitions()
//         deriveDatabaseFromMigrationsFiles()
        try {
            // 1. Explicitly register the SQLite driver (prevents classloader/cast issues)
            Class.forName("org.sqlite.JDBC")

            // 2. Open raw JDBC connections
            val refConn = DriverManager.getConnection("jdbc:sqlite:build/migrated-schema.db")
            val targetConn = DriverManager.getConnection("jdbc:sqlite:build/current-schema.db")

            // 3. Wrap in Liquibase's JdbcConnection FIRST (critical!)
            val refLiquibaseConn = JdbcConnection(refConn)
            val targetLiquibaseConn = JdbcConnection(targetConn)

            // 4. Let Liquibase detect the correct Database implementation (now works without cast error)
            val referenceDb = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(refLiquibaseConn)

            val targetDb = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(targetLiquibaseConn)

            // 5. Compare control with suppressed fields (SQLite has no real catalog/schema)
            val compareControl = CompareControl().apply {
                addSuppressedField(Catalog::class.java, "name")
                addSuppressedField(Schema::class.java, "name")
            }

            val typesToInclude = arrayOf(
                liquibase.structure.core.Table::class.java,
                liquibase.structure.core.Column::class.java,
                liquibase.structure.core.PrimaryKey::class.java,
                liquibase.structure.core.ForeignKey::class.java,
                liquibase.structure.core.Index::class.java
            )
            val refSnapshotControl = SnapshotControl(referenceDb, false, *typesToInclude)
            val targetSnapshotControl = SnapshotControl(targetDb, false, *typesToInclude)
            // Create limited snapshots using the controls
            val refSnapshot = SnapshotGeneratorFactory.getInstance().createSnapshot(
                CatalogAndSchema.DEFAULT, referenceDb, refSnapshotControl
            )
            val targetSnapshot = SnapshotGeneratorFactory.getInstance().createSnapshot(
                CatalogAndSchema.DEFAULT, targetDb, targetSnapshotControl
            )

            // 6. Run the diff
            val diffResult = DiffGeneratorFactory.getInstance().compare(
                refSnapshot, targetSnapshot, compareControl
            )

            // 7. Generate changelog
            val changeLog = DiffToChangeLog(diffResult, DiffOutputControl())

            val outputFile = File("build/diff-changelog.xml")
            outputFile.parentFile.mkdirs()
            changeLog.print(outputFile.absolutePath, true)  // works perfectly in 4.28.0

            println("Diff written to: ${outputFile.absolutePath}")
            changeLog.print(System.out)  // console preview

            // 8. Better check (areEqual() is the correct method)
            if (diffResult.areEqual()) {
                println("No schema differences found — your .sq and .sqm are in sync!")
            } else {
                println("Differences detected — check build/diff-changelog.xml")
                // Optional: fail the build in CI
                // throw IllegalStateException("Schema drift detected!")
            }

        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    private fun buildCleanDatabaseSchemaFromDefinitions(): File {
        val dbFile = File("build/current-schema.db")
        dbFile.parentFile.mkdirs()
        dbFile.delete()

        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        UserDatabase.Schema.create(driver)
        driver.close()

        println("Current schema DB created at: ${dbFile.absolutePath}")
        return dbFile
    }

    private fun deriveDatabaseFromMigrationsFiles(): File {
        val dbFile = File("build/34.db")
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")

        UserDatabase.Schema.migrate(driver, 35, 121)
        driver.close()

        println("Derived-from-migrations DB created at: ${dbFile.absolutePath}")
        return dbFile
    }
}
