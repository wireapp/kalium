/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class PooledJdbcSqliteDriverTest {

    private val directory = Files.createTempDirectory("pooled-driver").toFile()
    private val driver = databaseDriver(uri = jdbcUrl(directory.resolve("test.db"))) {
        isWALEnabled = true
    }
    private val transacter = object : TransacterImpl(driver) {}

    @BeforeTest
    fun createTable() {
        driver.execute(null, "CREATE TABLE item(value INTEGER NOT NULL)", 0)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
        directory.deleteRecursively()
    }

    @Test
    fun givenStatementsOutsideTransaction_whenTheyRunOneAfterAnother_thenTheyShareTheWriterConnection() {
        // Temporary tables only exist on the connection that created them.
        driver.execute(null, "CREATE TEMP TABLE scratch(value INTEGER)", 0)
        driver.execute(null, "INSERT INTO scratch VALUES (1)", 0)

        assertEquals(1L, driver.execute(null, "UPDATE scratch SET value = 2", 0).value)
    }

    @Test
    fun givenInsertOutsideTransaction_whenReadingLastInsertRowId_thenItComesFromTheSameConnection() {
        driver.execute(null, "INSERT INTO item VALUES (7)", 0)

        assertEquals(1L, driver.queryLong("SELECT last_insert_rowid()"))
    }

    @Test
    fun givenAttachedDatabase_whenQueryingIt_thenTheAliasIsAvailable() {
        val otherFile = directory.resolve("other.db")
        databaseDriver(uri = jdbcUrl(otherFile)).apply {
            execute(null, "CREATE TABLE other_item(value INTEGER)", 0)
            execute(null, "INSERT INTO other_item VALUES (1)", 0)
            close()
        }

        driver.execute(null, "ATTACH DATABASE '${otherFile.absolutePath}' AS other", 0)
        assertEquals(1L, driver.queryLong("SELECT count(*) FROM other.other_item"))
        driver.execute(null, "DETACH DATABASE other", 0)
    }

    @Test
    fun givenTransaction_whenReadingInside_thenItSeesItsOwnWrites() {
        transacter.transaction {
            driver.execute(null, "INSERT INTO item VALUES (1)", 0)
            assertEquals(1L, driver.countItems())
        }
    }

    @Test
    fun givenOpenTransactionOnAnotherThread_whenReading_thenTheReadDoesNotWaitAndSeesOnlyCommittedRows() {
        val inserted = CountDownLatch(1)
        val readFinished = CountDownLatch(1)
        val writer = thread {
            transacter.transaction {
                driver.execute(null, "INSERT INTO item VALUES (1)", 0)
                inserted.countDown()
                readFinished.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }
        }
        assertTrue(inserted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))

        // If this read waited for the writer, the transaction would only commit after the timeout and the count would be 1.
        val countDuringTransaction = driver.countItems()
        readFinished.countDown()
        writer.join()

        assertEquals(0L, countDuringTransaction)
        assertEquals(1L, driver.countItems())
    }

    @Test
    fun givenManyThreadsReadingAndWriting_whenTheyRunConcurrently_thenAllWritesLand() {
        val executor = Executors.newFixedThreadPool(THREADS)
        try {
            val tasks = (1..THREADS).map { index ->
                executor.submit {
                    repeat(WRITES_PER_THREAD) {
                        if (index % 2 == 0) {
                            transacter.transaction { driver.execute(null, "INSERT INTO item VALUES ($index)", 0) }
                        } else {
                            driver.execute(null, "INSERT INTO item VALUES ($index)", 0)
                        }
                        driver.countItems()
                    }
                }
            }
            tasks.forEach { it.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }

        assertEquals((THREADS * WRITES_PER_THREAD).toLong(), driver.countItems())
    }

    @Test
    fun givenFailedTransaction_whenTheNextOneRuns_thenTheWriterIsAvailableAgain() {
        assertFails {
            transacter.transaction {
                driver.execute(null, "INSERT INTO item VALUES (1)", 0)
                error("rolled back")
            }
        }

        transacter.transaction { driver.execute(null, "INSERT INTO item VALUES (2)", 0) }

        assertEquals(1L, driver.countItems())
        assertEquals(2L, driver.queryLong("SELECT value FROM item"))
    }

    private fun SqlDriver.countItems(): Long = queryLong("SELECT count(*) FROM item")

    private fun SqlDriver.queryLong(sql: String): Long = executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            cursor.next()
            QueryResult.Value(requireNotNull(cursor.getLong(0)))
        },
        parameters = 0
    ).value

    private companion object {
        const val THREADS = 8
        const val WRITES_PER_THREAD = 50
        const val TIMEOUT_SECONDS = 10L
    }
}
