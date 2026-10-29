/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.persistence.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.SuspendingTransacterImpl
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppleSuspensionSqlDriverTest {
    @Test
    fun synchronousReadAndWriteRemainSynchronousAndClassifyGlobalOperations() {
        val diagnostics = RecordingDiagnostics()
        val driver = AppleSuspensionSqlDriver(FakeDriver(), "global_database", diagnostics)
        assertEquals(42L, driver.read().value)
        assertEquals(1L, driver.execute(null, "UPDATE private SET value = ?", 1).value)
        assertEquals(listOf("global_database/read", "global_database/write"), diagnostics.started)
        assertEquals(listOf("success", "success"), diagnostics.finished)
        assertTrue(diagnostics.active.isEmpty())
    }

    @Test
    fun asyncReadStaysOutstandingThroughActualAwaitAndRepeatAwaitHasNewIdentity() = runTest {
        val gate = CompletableDeferred<Unit>()
        val native = FakeDriver().apply { query = { QueryResult.AsyncValue { gate.await(); 42L } } }
        val diagnostics = RecordingDiagnostics()
        val driver = AppleSuspensionSqlDriver(native, "user_database", diagnostics)
        val result = driver.read()
        assertTrue(result is QueryResult.AsyncValue)
        assertEquals(listOf("user_database/read"), diagnostics.active.values.toList())
        val pending = async { result.await() }
        runCurrent()
        assertTrue(diagnostics.finished.isEmpty())
        gate.complete(Unit)
        assertEquals(42L, pending.await())
        assertTrue(diagnostics.active.isEmpty())
        assertEquals(42L, result.await())
        assertEquals(2, diagnostics.started.size)
        assertEquals(2, diagnostics.finished.size)
        assertTrue(diagnostics.active.isEmpty())
    }

    @Test
    fun cancelledAsyncReadCompletesItsSpanWithoutChangingCancellation() = runTest {
        val gate = CompletableDeferred<Unit>()
        val native = FakeDriver().apply { query = { QueryResult.AsyncValue { gate.await(); 42L } } }
        val diagnostics = RecordingDiagnostics()
        val driver = AppleSuspensionSqlDriver(native, "user_database", diagnostics)
        val pending = async { driver.read().await() }
        runCurrent()
        pending.cancelAndJoin()
        assertTrue(pending.isCancelled)
        assertEquals(listOf("cancelled"), diagnostics.finished)
        assertTrue(diagnostics.active.isEmpty())
    }

    @Test
    fun synchronousFailureAndAsyncFailureEndSpansAndPreserveTheFailure() = runTest {
        val diagnostics = RecordingDiagnostics()
        val expected = IllegalStateException("synthetic failure")
        val native = FakeDriver().apply { query = { throw expected } }
        val driver = AppleSuspensionSqlDriver(native, "user_database", diagnostics)
        assertEquals(expected, assertFailsWith<IllegalStateException> { driver.read() })
        native.query = { QueryResult.AsyncValue { throw expected } }
        assertEquals(expected, assertFailsWith<IllegalStateException> { driver.read().await() })
        assertEquals(listOf("failure", "failure"), diagnostics.finished)
        assertTrue(diagnostics.active.isEmpty())
    }

    @Test
    fun onlyOuterTransactionIsCountedUntilCommitAndRollback() = runTest {
        val diagnostics = RecordingDiagnostics()
        val driver = AppleSuspensionSqlDriver(FakeDriver(), "user_database", diagnostics)
        val transacter = object : SuspendingTransacterImpl(driver) {}
        transacter.transaction {
            assertEquals(listOf("user_database/transaction"), diagnostics.active.values.toList())
            transacter.transaction {
                assertEquals(1, diagnostics.active.size)
                assertEquals(42L, driver.read().await())
            }
            assertEquals(listOf("user_database/transaction"), diagnostics.active.values.toList())
        }
        assertTrue(diagnostics.active.isEmpty())
        assertEquals(listOf("success", "commit"), diagnostics.finished)
        transacter.transaction { rollback() }
        assertEquals("rollback", diagnostics.finished.last())
        assertTrue(diagnostics.active.isEmpty())
    }

    @Test
    fun failedNativeTransactionEndKeepsAnOutstandingObservation() = runTest {
        val native = FakeDriver().apply { endFailure = IllegalStateException("native end failed") }
        val diagnostics = RecordingDiagnostics()
        val driver = AppleSuspensionSqlDriver(native, "user_database", diagnostics)
        val transacter = object : SuspendingTransacterImpl(driver) {}
        assertFailsWith<IllegalStateException> { transacter.transaction { } }
        assertEquals(listOf("user_database/transaction"), diagnostics.active.values.toList())
        assertTrue(diagnostics.finished.isEmpty())
    }

    @Test
    fun throwingCommitListenerEndsTheCommittedObservationAndPreservesTheFailure() = runTest {
        val expected = IllegalStateException("listener failed")
        val native = FakeDriver().apply { notificationFailure = expected }
        val diagnostics = RecordingDiagnostics()
        val driver = AppleSuspensionSqlDriver(native, "user_database", diagnostics)
        val transacter = object : SuspendingTransacterImpl(driver) {
            fun changed() = notifyQueries(1) { it("test_table") }
        }
        val actual = assertFailsWith<IllegalStateException> {
            transacter.transaction { transacter.changed() }
        }
        assertEquals(expected, actual)
        assertTrue(diagnostics.active.isEmpty())
        assertEquals(listOf("post_commit_failure"), diagnostics.finished)
        // A subsequent transaction must not complete or erase the earlier observation again.
        native.notificationFailure = null
        transacter.transaction { transacter.changed() }
        assertEquals(listOf("post_commit_failure", "commit"), diagnostics.finished)
        assertTrue(diagnostics.active.isEmpty())
    }

    @Test
    fun asynchronousTransactionCreationRemainsPendingUntilCompletionHook() = runTest {
        val gate = CompletableDeferred<Unit>()
        val native = FakeDriver().apply { transactionGate = gate }
        val diagnostics = RecordingDiagnostics()
        val driver = AppleSuspensionSqlDriver(native, "global_database", diagnostics)
        val transacter = object : SuspendingTransacterImpl(driver) {}
        val pending = async {
            transacter.transaction {
                assertEquals(listOf("global_database/transaction"), diagnostics.active.values.toList())
            }
        }
        runCurrent()
        assertEquals(1, diagnostics.active.size)
        gate.complete(Unit)
        pending.await()
        assertEquals(listOf("commit"), diagnostics.finished)
        assertTrue(diagnostics.active.isEmpty())
    }

    private fun SqlDriver.read(): QueryResult<Long> = executeQuery(null, "SELECT private FROM confidential", { QueryResult.Value(0L) }, 0)

    private class RecordingDiagnostics : DatabaseOperationDiagnostics {
        private var next = 0L
        val active = mutableMapOf<Long, String>()
        val started = mutableListOf<String>()
        val finished = mutableListOf<String>()
        override fun begin(databaseKind: String, operation: String): Long {
            val token = ++next
            active[token] = "$databaseKind/$operation"
            started += "$databaseKind/$operation"
            return token
        }
        override fun end(token: Long, outcome: String) {
            check(active.remove(token) != null) { "A span completed twice" }
            finished += outcome
        }
    }

    private class FakeDriver : SqlDriver {
        var query: () -> QueryResult<Long> = { QueryResult.Value(42L) }
        var transactionGate: CompletableDeferred<Unit>? = null
        var endFailure: Throwable? = null
        var notificationFailure: Throwable? = null
        private var transaction: Transacter.Transaction? = null

        override fun execute(identifier: Int?, sql: String, parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?): QueryResult<Long> =
            QueryResult.Value(1L)

        @Suppress("UNCHECKED_CAST")
        override fun <R> executeQuery(identifier: Int?, sql: String, mapper: (SqlCursor) -> QueryResult<R>, parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?): QueryResult<R> = query() as QueryResult<R>

        override fun newTransaction(): QueryResult<Transacter.Transaction> = transactionGate?.let { gate ->
            QueryResult.AsyncValue { gate.await(); createTransaction() }
        } ?: QueryResult.Value(createTransaction())

        private fun createTransaction(): Transacter.Transaction {
            val enclosing = transaction
            return object : Transacter.Transaction() {
                override val enclosingTransaction: Transacter.Transaction? = enclosing
                override fun endTransaction(successful: Boolean): QueryResult<Unit> {
                    endFailure?.let { throw it }
                    transaction = enclosing
                    return QueryResult.Unit
                }
            }.also { transaction = it }
        }

        override fun currentTransaction(): Transacter.Transaction? = transaction
        override fun addListener(vararg queryKeys: String, listener: Query.Listener) = Unit
        override fun removeListener(vararg queryKeys: String, listener: Query.Listener) = Unit
        override fun notifyListeners(vararg queryKeys: String) {
            notificationFailure?.let { throw it }
        }
        override fun close() = Unit
    }
}
