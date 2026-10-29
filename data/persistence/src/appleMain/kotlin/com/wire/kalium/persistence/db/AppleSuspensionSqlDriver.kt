/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.persistence.db

import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import com.wire.kalium.logger.AppleSuspensionDiagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import platform.Foundation.NSThread

/**
 * Observes API operations, including standalone reads outside account write admission. These are not VFS lock
 * counts. Native synchronous results stay synchronous. Async results remain outstanding until await completes;
 * a transaction stays outstanding until SQLDelight's commit/rollback callback, or an observed post-commit
 * listener failure. If native transaction end fails, its missing end remains visible rather than claiming release.
 */
@Suppress("TooGenericExceptionCaught") // Observe failures without replacing or swallowing the driver's throwable.
internal class AppleSuspensionSqlDriver(
    private val delegate: SqlDriver,
    private val databaseKind: String,
    private val diagnostics: DatabaseOperationDiagnostics = SuspensionDatabaseOperations,
) : SqlDriver by delegate {
    private val transactionObservations = MutableStateFlow<Map<NSThread, TransactionObservation>>(emptyMap())

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> = tracked("write") { delegate.execute(identifier, sql, parameters, binders) }

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> {
        // Match NativeSqliteDriver's reader/writer routing. Never record SQL or bound values.
        val statement = sql.trimStart().uppercase()
        val operation = if (statement.startsWith("UPDATE") || statement.startsWith("INSERT") || statement.startsWith("DELETE")) {
            "write"
        } else "read"
        return tracked(operation) { delegate.executeQuery(identifier, sql, mapper, parameters, binders) }
    }

    override fun newTransaction(): QueryResult<Transacter.Transaction> {
        // Nested transactions share the native outer transaction and its completion hooks.
        if (delegate.currentTransaction() != null) return delegate.newTransaction()
        return tracked("transaction", complete = { token, transaction ->
            val observation = TransactionObservation(token, NSThread.currentThread)
            if (token != 0L) transactionObservations.update { it + (observation.thread to observation) }
            transaction.afterCommit { observation.finish("commit") }
            transaction.afterRollback { observation.finish("rollback") }
        }) { delegate.newTransaction() }
    }

    override fun notifyListeners(vararg queryKeys: String) {
        // SQLDelight notifies listeners after native commit, before its afterCommit hooks. A throwing
        // listener must not leave that completed transaction counted as active forever. Capture this
        // thread's observation before callbacks, which can themselves start another transaction.
        val observation = transactionObservations.value[NSThread.currentThread]
            .takeIf { delegate.currentTransaction() == null }
        try {
            delegate.notifyListeners(*queryKeys)
        } catch (failure: Throwable) {
            observation?.finish("post_commit_failure")
            throw failure
        }
    }

    override fun close() {
        val token = diagnostics.begin(databaseKind, "close")
        try {
            delegate.close()
            diagnostics.end(token, "success")
        } catch (failure: Throwable) {
            diagnostics.end(token, failure.outcome())
            throw failure
        }
    }

    private fun <T> tracked(
        operation: String,
        complete: (Long, T) -> Unit = { token, _ -> diagnostics.end(token, "success") },
        block: () -> QueryResult<T>,
    ): QueryResult<T> {
        val token = diagnostics.begin(databaseKind, operation)
        val result = try {
            block()
        } catch (failure: Throwable) {
            diagnostics.end(token, failure.outcome())
            throw failure
        }
        return when (result) {
            is QueryResult.Value -> {
                finish(token, result.value, complete)
                result
            }
            is QueryResult.AsyncValue -> {
                val firstAwait = MutableStateFlow(true)
                QueryResult.AsyncValue {
                    // QueryResult.AsyncValue may be awaited more than once; preserve that behavior and give
                    // each execution its own span, instead of completing the original acquisition twice.
                    val awaiting = if (firstAwait.compareAndSet(true, false)) token else diagnostics.begin(databaseKind, operation)
                    val value = try {
                        result.await()
                    } catch (failure: Throwable) {
                        diagnostics.end(awaiting, failure.outcome())
                        throw failure
                    }
                    finish(awaiting, value, complete)
                    value
                }
            }
        }
    }

    private fun <T> finish(token: Long, value: T, complete: (Long, T) -> Unit) {
        try {
            complete(token, value)
        } catch (failure: Throwable) {
            diagnostics.end(token, failure.outcome())
            throw failure
        }
    }

    private fun Throwable.outcome(): String = if (this is CancellationException) "cancelled" else "failure"

    private inner class TransactionObservation(val token: Long, val thread: NSThread) {
        private val completed = MutableStateFlow(false)

        fun finish(outcome: String) {
            if (!completed.compareAndSet(false, true)) return
            transactionObservations.update { current ->
                if (current[thread] === this) current - thread else current
            }
            diagnostics.end(token, outcome)
        }
    }
}

internal interface DatabaseOperationDiagnostics {
    fun begin(databaseKind: String, operation: String): Long
    fun end(token: Long, outcome: String)
}

private object SuspensionDatabaseOperations : DatabaseOperationDiagnostics {
    override fun begin(databaseKind: String, operation: String): Long =
        AppleSuspensionDiagnostics.beginDatabaseOperation(databaseKind, operation)
    override fun end(token: Long, outcome: String) = AppleSuspensionDiagnostics.endDatabaseOperation(token, outcome)
}
