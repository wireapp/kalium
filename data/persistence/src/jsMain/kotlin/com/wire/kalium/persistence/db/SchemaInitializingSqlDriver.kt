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
import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise

internal class SchemaInitializingSqlDriver(
    private val delegate: SqlDriver,
    initialize: suspend () -> Unit,
) : SqlDriver {

    private val initialized = CompletableDeferred<Unit>()

    init {
        GlobalScope.promise {
            initialize()
            initialized.complete(Unit)
        }.catch { error ->
            initialized.completeExceptionally(error)
        }
    }

    override fun addListener(vararg queryKeys: String, listener: Query.Listener) {
        delegate.addListener(*queryKeys, listener = listener)
    }

    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) {
        delegate.removeListener(*queryKeys, listener = listener)
    }

    override fun notifyListeners(vararg queryKeys: String) {
        delegate.notifyListeners(*queryKeys)
    }

    override fun close() {
        delegate.close()
    }

    override fun currentTransaction(): Transacter.Transaction? = delegate.currentTransaction()

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> = QueryResult.AsyncValue {
        initialized.await()
        delegate.executeQuery(identifier, sql, mapper, parameters, binders).await()
    }

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> = QueryResult.AsyncValue {
        initialized.await()
        delegate.execute(identifier, sql, parameters, binders).await()
    }

    override fun newTransaction(): QueryResult<Transacter.Transaction> = QueryResult.AsyncValue {
        initialized.await()
        delegate.newTransaction().await()
    }
}
