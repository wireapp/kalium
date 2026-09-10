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

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.jdbc.JdbcPreparedStatement
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Properties
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * SQLDelight driver for a file-backed SQLite database that keeps its connections open.
 *
 * SQLDelight's `JdbcSqliteDriver` opens a new connection for every statement outside a transaction
 * and closes it right after. Each statement then pays for opening the file, loading the schema and,
 * on an encrypted database, setting up the cipher. Per-connection state such as an attached
 * database is gone again by the next statement.
 *
 * This driver follows the model Android's SQLite uses for WAL databases instead:
 * - One writer connection, guarded by a lock. Write statements and all transactions run on it. A
 *   transaction holds the lock until it ends, so its reads see its own uncommitted writes.
 * - Up to `maxReaders` reader connections for queries outside a transaction. In WAL mode they read
 *   the last committed state while the writer carries on.
 *
 * Connections are opened on first use and stay open until [close].
 */
@Suppress("TooManyFunctions")
internal class PooledJdbcSqliteDriver(
    private val url: String,
    private val properties: Properties,
    maxReaders: Int = DEFAULT_MAX_READERS,
) : SqlDriver {

    private val listeners = linkedMapOf<String, MutableSet<Query.Listener>>()

    private val writerLock = ReentrantLock()
    private var writer: Connection? = null

    private val readerPermits = Semaphore(maxReaders)
    private val idleReaders = ConcurrentLinkedQueue<Connection>()
    private val readerReturnLock = Any()

    private val transactions = ThreadLocal<Transaction?>()

    // ATTACH only affects the connection it runs on. While a database is attached to the writer,
    // queries stay there so they can reach it.
    private val attachedDatabases = AtomicInteger(0)

    @Volatile
    private var isClosed = false

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> {
        val updatedRows = onWriter { connection ->
            connection.prepareStatement(sql).use { statement ->
                JdbcPreparedStatement(statement)
                    .apply { if (binders != null) this.binders() }
                    .execute()
            }
        }
        trackAttachedDatabases(sql)
        return QueryResult.Value(updatedRows)
    }

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> {
        val query = { connection: Connection ->
            JdbcPreparedStatement(connection.prepareStatement(sql))
                .apply { if (binders != null) this.binders() }
                .executeQuery(mapper)
        }
        return if (transactions.get() == null && runsOnReader(sql)) onReader(query) else onWriter(query)
    }

    override fun newTransaction(): QueryResult<Transacter.Transaction> {
        val enclosing = transactions.get()
        val transaction = if (enclosing != null) {
            Transaction(enclosing, enclosing.connection)
        } else {
            Transaction(null, beginOnWriter())
        }
        transactions.set(transaction)
        return QueryResult.Value(transaction)
    }

    override fun currentTransaction(): Transacter.Transaction? = transactions.get()

    override fun addListener(vararg queryKeys: String, listener: Query.Listener) {
        synchronized(listeners) {
            queryKeys.forEach { listeners.getOrPut(it) { linkedSetOf() }.add(listener) }
        }
    }

    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) {
        synchronized(listeners) {
            queryKeys.forEach { listeners[it]?.remove(listener) }
        }
    }

    override fun notifyListeners(vararg queryKeys: String) {
        val listenersToNotify = linkedSetOf<Query.Listener>()
        synchronized(listeners) {
            queryKeys.forEach { listeners[it]?.let(listenersToNotify::addAll) }
        }
        listenersToNotify.forEach(Query.Listener::queryResultsChanged)
    }

    override fun close() {
        isClosed = true
        writerLock.withLock {
            writer?.close()
            writer = null
        }
        synchronized(readerReturnLock) {
            generateSequence { idleReaders.poll() }.forEach(Connection::close)
        }
    }

    /** Takes the writer lock and keeps it until the transaction ends, see [Transaction.endTransaction]. */
    private fun beginOnWriter(): Connection {
        writerLock.lock()
        var hasBegun = false
        try {
            // IMMEDIATE takes SQLite's write lock up front, so a transaction that reads first can't
            // fail with SQLITE_BUSY later because another process wrote in between.
            return writerConnection().also {
                it.executeStatement("BEGIN IMMEDIATE")
                hasBegun = true
            }
        } finally {
            if (!hasBegun) writerLock.unlock()
        }
    }

    private fun <T> onWriter(block: (Connection) -> T): T {
        val transaction = transactions.get()
        return if (transaction != null) {
            block(transaction.connection)
        } else {
            writerLock.withLock { block(writerConnection()) }
        }
    }

    /** Callers must hold [writerLock]. */
    private fun writerConnection(): Connection = writer ?: openConnection().also { writer = it }

    private fun <T> onReader(block: (Connection) -> T): T {
        readerPermits.acquire()
        try {
            val reader = idleReaders.poll() ?: openConnection()
            try {
                return block(reader)
            } finally {
                returnReader(reader)
            }
        } finally {
            readerPermits.release()
        }
    }

    private fun returnReader(reader: Connection) {
        synchronized(readerReturnLock) {
            if (isClosed) reader.close() else idleReaders.offer(reader)
        }
    }

    private fun openConnection(): Connection {
        check(!isClosed) { "The database driver is closed" }
        return DriverManager.getConnection(url, properties)
    }

    private fun runsOnReader(sql: String): Boolean {
        val statement = sql.trimStart()
        val isQuery = statement.startsWith("SELECT", ignoreCase = true) || statement.startsWith("WITH", ignoreCase = true)
        return isQuery && attachedDatabases.get() == 0 && !WRITER_ONLY_SQL.containsMatchIn(statement)
    }

    private fun trackAttachedDatabases(sql: String) {
        val statement = sql.trimStart()
        when {
            statement.startsWith("ATTACH", ignoreCase = true) -> attachedDatabases.incrementAndGet()
            statement.startsWith("DETACH", ignoreCase = true) -> attachedDatabases.updateAndGet { (it - 1).coerceAtLeast(0) }
        }
    }

    private inner class Transaction(
        override val enclosingTransaction: Transaction?,
        val connection: Connection,
    ) : Transacter.Transaction() {

        override fun endTransaction(successful: Boolean): QueryResult<Unit> {
            try {
                if (enclosingTransaction == null) finishOnWriter(successful)
            } finally {
                transactions.set(enclosingTransaction)
            }
            return QueryResult.Unit
        }

        private fun finishOnWriter(successful: Boolean) {
            try {
                if (successful) connection.commitOrRollBack() else connection.executeStatement("ROLLBACK TRANSACTION")
            } finally {
                writerLock.unlock()
            }
        }
    }

    private companion object {
        // Bounds the page cache SQLite keeps per connection (about 2 MB by default) under heavy parallel reads.
        const val DEFAULT_MAX_READERS = 8

        // RETURNING makes a write look like a query. The functions report on the previous statement
        // of the same connection, so they have to run where that statement ran.
        val WRITER_ONLY_SQL = Regex("""\bRETURNING\b|\b(last_insert_rowid|changes|total_changes)\s*\(""", RegexOption.IGNORE_CASE)
    }
}

private fun Connection.executeStatement(sql: String) {
    prepareStatement(sql).use { it.execute() }
}

/**
 * A failed COMMIT leaves the transaction open, and the next BEGIN on this connection would fail.
 * Rolling back keeps the writer usable; the commit error still reaches the caller.
 */
private fun Connection.commitOrRollBack() {
    try {
        executeStatement("END TRANSACTION")
    } catch (exception: SQLException) {
        runCatching { executeStatement("ROLLBACK TRANSACTION") }
        throw exception
    }
}
