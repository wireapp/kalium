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
import com.wire.kalium.persistence.kaliumLogger
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import platform.Foundation.NSThread

internal class TracingSqlDriver(
    private val delegate: SqlDriver,
    private val label: String,
) : SqlDriver by delegate {

    private val logger = kaliumLogger.withTextTag("TracingSqlDriver")
    private var currentSlowBurst: SlowBurst? = null

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?
    ): QueryResult<Long> {
        val startedAt = Clock.System.now()
        return try {
            delegate.execute(identifier, sql, parameters, binders).also {
                logStatement(
                    kind = "execute",
                    sql = sql,
                    identifier = identifier,
                    parameters = parameters,
                    elapsedMs = (Clock.System.now() - startedAt).inWholeMilliseconds,
                )
            }
        } catch (t: Throwable) {
            logStatement(
                kind = "execute",
                sql = sql,
                identifier = identifier,
                parameters = parameters,
                elapsedMs = (Clock.System.now() - startedAt).inWholeMilliseconds,
                throwable = t,
            )
            throw t
        }
    }

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?
    ): QueryResult<R> {
        val startedAt = Clock.System.now()
        return try {
            delegate.executeQuery(identifier, sql, mapper, parameters, binders).also {
                logStatement(
                    kind = "query",
                    sql = sql,
                    identifier = identifier,
                    parameters = parameters,
                    elapsedMs = (Clock.System.now() - startedAt).inWholeMilliseconds,
                )
            }
        } catch (t: Throwable) {
            logStatement(
                kind = "query",
                sql = sql,
                identifier = identifier,
                parameters = parameters,
                elapsedMs = (Clock.System.now() - startedAt).inWholeMilliseconds,
                throwable = t,
            )
            throw t
        }
    }

    override fun newTransaction(): QueryResult<Transacter.Transaction> {
        val startedAt = Clock.System.now()
        return delegate.newTransaction().also {
            val elapsedMs = (Clock.System.now() - startedAt).inWholeMilliseconds
            val currentTransaction = delegate.currentTransaction()
            logger.i(
                "[SqlTrace] db=$label kind=new-transaction elapsedMs=$elapsedMs " +
                    "thread=${threadLabel()} inTransaction=${currentTransaction != null} " +
                    "transactionId=${currentTransaction?.hashCode() ?: -1}"
            )
        }
    }

    override fun addListener(vararg queryKeys: String, listener: Query.Listener) {
        logger.i(
            "[SqlListener] db=$label action=add queryKeys=${queryKeys.joinToString(",")} " +
                "listener=${listener.hashCode()} thread=${threadLabel()}"
        )
        delegate.addListener(*queryKeys, listener = listener)
    }

    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) {
        logger.i(
            "[SqlListener] db=$label action=remove queryKeys=${queryKeys.joinToString(",")} " +
                "listener=${listener.hashCode()} thread=${threadLabel()}"
        )
        delegate.removeListener(*queryKeys, listener = listener)
    }

    override fun notifyListeners(vararg queryKeys: String) {
        logger.w(
            "[SqlListener] db=$label action=notify keyCount=${queryKeys.size} " +
                "queryKeys=${queryKeys.joinToString(",")} thread=${threadLabel()} " +
                "inTransaction=${delegate.currentTransaction() != null}"
        )
        delegate.notifyListeners(*queryKeys)
    }

    override fun close() {
        delegate.close()
    }

    private fun logStatement(
        kind: String,
        sql: String,
        identifier: Int?,
        parameters: Int,
        elapsedMs: Long,
        throwable: Throwable? = null,
    ) {
        val now = Clock.System.now()
        val currentTransaction = delegate.currentTransaction()
        val message =
            "[SqlTrace] db=$label kind=$kind elapsedMs=$elapsedMs identifier=${identifier ?: -1} " +
                "parameters=$parameters thread=${threadLabel()} inTransaction=${currentTransaction != null} " +
                "transactionId=${currentTransaction?.hashCode() ?: -1} sql=${normalizeSql(sql)}"
        val errorDetail = throwable?.let { " error=${describeThrowable(it)}" }.orEmpty()
        when {
            throwable != null -> logger.w(message + errorDetail)
            elapsedMs >= SLOW_SQL_THRESHOLD_MS -> logger.w(message)
            else -> logger.i(message)
        }
        updateSlowBurst(now, kind, normalizeSql(sql), elapsedMs, throwable)
    }

    private fun updateSlowBurst(
        now: Instant,
        kind: String,
        normalizedSql: String,
        elapsedMs: Long,
        throwable: Throwable?,
    ) {
        val shouldTrack = elapsedMs >= SLOW_SQL_THRESHOLD_MS || throwable != null
        if (!shouldTrack) {
            flushSlowBurst(now)
            return
        }

        val burst = currentSlowBurst
        if (burst == null || (now - burst.lastSeenAt).inWholeMilliseconds > BURST_GAP_THRESHOLD_MS) {
            flushSlowBurst(now)
            val initialErrors = linkedSetOf<String>()
            throwable?.let { initialErrors += describeThrowable(it) }
            currentSlowBurst = SlowBurst(
                startedAt = now,
                lastSeenAt = now,
                count = 1,
                maxElapsedMs = elapsedMs,
                sampleKinds = linkedSetOf(kind),
                sampleSql = linkedSetOf(normalizedSql),
                errorKinds = initialErrors,
            )
            return
        }

        burst.lastSeenAt = now
        burst.count += 1
        if (elapsedMs > burst.maxElapsedMs) burst.maxElapsedMs = elapsedMs
        if (burst.sampleKinds.size < MAX_BURST_KIND_SAMPLES) burst.sampleKinds += kind
        if (burst.sampleSql.size < MAX_BURST_SQL_SAMPLES) burst.sampleSql += normalizedSql
        throwable?.let {
            if (burst.errorKinds.size < MAX_BURST_ERROR_SAMPLES) burst.errorKinds += describeThrowable(it)
        }
    }

    private fun flushSlowBurst(now: Instant) {
        val burst = currentSlowBurst ?: return
        currentSlowBurst = null
        if (burst.count < SLOW_BURST_MIN_COUNT) return
        val durationMs = (burst.lastSeenAt - burst.startedAt).inWholeMilliseconds
        val idleMs = (now - burst.lastSeenAt).inWholeMilliseconds
        logger.w(
            "[SqlBurst] db=$label count=${burst.count} durationMs=$durationMs idleMs=$idleMs " +
                "maxElapsedMs=${burst.maxElapsedMs} kinds=${burst.sampleKinds.joinToString(",")} " +
                "errors=${burst.errorKinds.ifEmpty { setOf("none") }.joinToString("|")} " +
                "sql=${burst.sampleSql.joinToString(" || ")}"
        )
    }

    private fun describeThrowable(throwable: Throwable): String {
        val parts = buildList {
            var current: Throwable? = throwable
            var depth = 0
            while (current != null && depth < MAX_THROWABLE_DEPTH) {
                val className = current::class.simpleName ?: "Throwable"
                val message = current.message?.replace(Regex("\\s+"), " ")?.trim()?.take(MAX_ERROR_MESSAGE_LENGTH)
                add(if (message.isNullOrBlank()) className else "$className:$message")
                current = current.cause
                depth += 1
            }
        }
        return parts.joinToString(" -> ")
    }

    private fun threadLabel(): String {
        val currentThread = NSThread.currentThread
        val threadName = currentThread.name?.takeIf { it.isNotBlank() } ?: "unnamed"
        return "$threadName(main=${NSThread.isMainThread})"
    }

    private fun normalizeSql(sql: String): String = sql.replace(Regex("\\s+"), " ").trim().take(MAX_SQL_LOG_LENGTH)

    private companion object {
        private const val SLOW_SQL_THRESHOLD_MS = 50L
        private const val MAX_SQL_LOG_LENGTH = 240
        private const val MAX_ERROR_MESSAGE_LENGTH = 160
        private const val MAX_THROWABLE_DEPTH = 4
        private const val BURST_GAP_THRESHOLD_MS = 150L
        private const val SLOW_BURST_MIN_COUNT = 4
        private const val MAX_BURST_KIND_SAMPLES = 4
        private const val MAX_BURST_SQL_SAMPLES = 4
        private const val MAX_BURST_ERROR_SAMPLES = 4
    }
}

private data class SlowBurst(
    val startedAt: Instant,
    var lastSeenAt: Instant,
    var count: Int,
    var maxElapsedMs: Long,
    val sampleKinds: LinkedHashSet<String>,
    val sampleSql: LinkedHashSet<String>,
    val errorKinds: LinkedHashSet<String>,
)
