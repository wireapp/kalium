/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.wire.kalium.persistence.db

import app.cash.sqldelight.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import com.wire.kalium.util.AccountResourceAccess

/**
 * Keeps NativeSqliteDriver's WAL reader pool available while account writes are paused.
 * Transactions keep admission until SQLDelight reports commit or rollback, including nested work.
 * A failed native transaction end must not release ownership through a finally block.
 */
internal class AppleWriteAccessSqlDriver(
    private val delegate: SqlDriver,
    private val writes: AccountResourceAccess,
) : SqlDriver by delegate {
    private val observedKeys = MutableStateFlow<Set<String>>(emptySet())

    override fun addListener(vararg queryKeys: String, listener: Query.Listener) {
        observedKeys.update { it + queryKeys }
        delegate.addListener(*queryKeys, listener = listener)
    }

    @Suppress("SpreadOperator") // SQLDelight accepts query keys as varargs.
    fun refreshReads() = delegate.notifyListeners(*observedKeys.value.toTypedArray())

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> = write { delegate.execute(identifier, sql, parameters, binders) }

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> {
        // Match NativeSqliteDriver's routing for DML RETURNING. All other queries outside a
        // transaction use its query_only reader connections; SQLite rejects writes there.
        val statement = sql.trimStart().uppercase()
        return if (statement.startsWith("UPDATE") || statement.startsWith("INSERT") || statement.startsWith("DELETE")) {
            write { delegate.executeQuery(identifier, sql, mapper, parameters, binders) }
        } else delegate.executeQuery(identifier, sql, mapper, parameters, binders)
    }

    override fun newTransaction(): QueryResult<Transacter.Transaction> {
        if (delegate.currentTransaction() != null) return delegate.newTransaction()
        return QueryResult.AsyncValue {
            val permit = writes.acquirePermit()
            try {
                delegate.newTransaction().await().also {
                    it.afterCommit(permit::release)
                    it.afterRollback(permit::release)
                }
            } catch (@Suppress("TooGenericExceptionCaught") failure: Throwable) {
                permit.release()
                throw failure
            }
        }
    }

    @Suppress("ReturnCount") // Preserve the native synchronous path when admission is available.
    private fun <T> write(operation: () -> QueryResult<T>): QueryResult<T> {
        if (delegate.currentTransaction() != null) return operation()
        val permit = writes.tryAcquirePermit()
        if (permit != null) {
            return try {
                // NativeSqliteDriver finishes the statement synchronously. Preserve that contract
                // for constructor initialization and other synchronous storage entry points.
                QueryResult.Value(operation().value)
            } finally {
                permit.release()
            }
        }
        return QueryResult.AsyncValue { writes.withAccess { operation().await() } }
    }
}

/** Requery retained observers after the NSE may have committed changes through its own driver. */
fun UserDatabaseBuilder.refreshAfterExternalWrites() {
    ((sqlDriver as? MutedSqlDriver)?.delegate as? AppleWriteAccessSqlDriver)?.refreshReads()
}
