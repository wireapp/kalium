/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.persistence.db.support

import android.content.Context
import android.database.Cursor
import android.util.Pair
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement
import com.wire.kalium.persistence.db.LiteSyncNodeType
import org.sqlite.database.sqlite.SQLiteDatabase

/**
 * A [SupportSQLiteOpenHelper.Factory] that creates LiteSync-enabled database connections.
 *
 * LiteSync enables SQLite database replication across devices. This factory wraps
 * LiteSync's SQLite bindings to be compatible with SQLDelight's AndroidSqliteDriver.
 *
 * Note: LiteSync does not support encryption. Do not use this with encrypted databases.
 *
 * @property syncUri The LiteSync server URI (e.g., "tcp://192.168.1.100:1234")
 * @property nodeType The node type: PRIMARY for the main node, SECONDARY for replicas
 * @property enableWriteAheadLogging Whether to enable WAL mode (recommended for performance)
 * @property onDatabaseReady Optional callback invoked when the database sync is ready
 * @property onDatabaseSync Optional callback invoked when a sync event occurs
 */
class LiteSyncOpenHelperFactory(
    private val syncUri: String,
    private val nodeType: LiteSyncNodeType = LiteSyncNodeType.SECONDARY,
    private val enableWriteAheadLogging: Boolean = true,
    private val onDatabaseReady: (() -> Unit)? = null,
    private val onDatabaseSync: (() -> Unit)? = null
) : SupportSQLiteOpenHelper.Factory {

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        return LiteSyncOpenHelper(
            context = configuration.context,
            name = configuration.name,
            version = configuration.callback.version,
            callback = configuration.callback,
            syncUri = syncUri,
            nodeType = nodeType,
            enableWriteAheadLogging = enableWriteAheadLogging,
            onDatabaseReady = onDatabaseReady,
            onDatabaseSync = onDatabaseSync
        )
    }
}

/**
 * A [SupportSQLiteOpenHelper] implementation that uses LiteSync for database replication.
 *
 * LiteSync requires databases to be opened with a special URI format that includes
 * sync configuration. This helper manages the database lifecycle and wraps the
 * LiteSync SQLiteDatabase for compatibility with AndroidX SQLite.
 */
private class LiteSyncOpenHelper(
    private val context: Context,
    private val name: String?,
    private val version: Int,
    private val callback: SupportSQLiteOpenHelper.Callback,
    private val syncUri: String,
    private val nodeType: LiteSyncNodeType,
    private val enableWriteAheadLogging: Boolean,
    private val onDatabaseReady: (() -> Unit)?,
    private val onDatabaseSync: (() -> Unit)?
) : SupportSQLiteOpenHelper {

    private var database: LiteSyncDatabase? = null
    private var isInitializing = false

    override val databaseName: String?
        get() = name

    override val writableDatabase: SupportSQLiteDatabase
        get() = getDatabase()

    override val readableDatabase: SupportSQLiteDatabase
        get() = getDatabase()

    @Synchronized
    private fun getDatabase(): SupportSQLiteDatabase {
        if (isInitializing) {
            throw IllegalStateException("getDatabase called recursively")
        }

        database?.let { db ->
            if (db.isOpen) return db
        }

        try {
            isInitializing = true

            // Build LiteSync URI with sync configuration
            val dbPath = if (name != null) {
                context.getDatabasePath(name).absolutePath
            } else {
                ":memory:"
            }

            val connectParam = when (nodeType) {
                LiteSyncNodeType.PRIMARY -> "bind"
                LiteSyncNodeType.SECONDARY -> "connect"
            }

            val liteSyncUri = "file:$dbPath?node=${nodeType.value}&$connectParam=$syncUri"

            // Open database using LiteSync URI format
            val liteSyncDb = SQLiteDatabase.openOrCreateDatabase(liteSyncUri, null)

            // Set up LiteSync callbacks
            onDatabaseReady?.let { readyCallback ->
                liteSyncDb.onReady { readyCallback() }
            }
            onDatabaseSync?.let { syncCallback ->
                liteSyncDb.onSync { syncCallback() }
            }

            // Check if we need to create or upgrade the schema
            val currentVersion = liteSyncDb.version
            val wrappedDb = LiteSyncDatabase(liteSyncDb)

            callback.onConfigure(wrappedDb)

            when {
                currentVersion == 0 -> {
                    liteSyncDb.beginTransaction()
                    try {
                        callback.onCreate(wrappedDb)
                        liteSyncDb.version = version
                        liteSyncDb.setTransactionSuccessful()
                    } finally {
                        liteSyncDb.endTransaction()
                    }
                }
                currentVersion < version -> {
                    liteSyncDb.beginTransaction()
                    try {
                        callback.onUpgrade(wrappedDb, currentVersion, version)
                        liteSyncDb.version = version
                        liteSyncDb.setTransactionSuccessful()
                    } finally {
                        liteSyncDb.endTransaction()
                    }
                }
                currentVersion > version -> {
                    liteSyncDb.beginTransaction()
                    try {
                        callback.onDowngrade(wrappedDb, currentVersion, version)
                        liteSyncDb.version = version
                        liteSyncDb.setTransactionSuccessful()
                    } finally {
                        liteSyncDb.endTransaction()
                    }
                }
            }

            callback.onOpen(wrappedDb)

            if (enableWriteAheadLogging) {
                liteSyncDb.enableWriteAheadLogging()
            }

            database = wrappedDb
            return wrappedDb
        } finally {
            isInitializing = false
        }
    }

    override fun close() {
        database?.close()
        database = null
    }

    override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
        database?.let { db ->
            if (enabled) {
                db.enableWriteAheadLogging()
            } else {
                db.disableWriteAheadLogging()
            }
        }
    }
}

/**
 * Wraps LiteSync's SQLiteDatabase to implement [SupportSQLiteDatabase].
 * Note: Uses internal accessor to avoid type conflicts between LiteSync's Pair type
 * and AndroidX SQLite's Pair type.
 */
private class LiteSyncDatabase(
    delegate: SQLiteDatabase
) : SupportSQLiteDatabase {

    // Store as Any to prevent type inference conflicts with delegate's methods
    private val _delegate: Any = delegate
    private val delegate: SQLiteDatabase get() = _delegate as SQLiteDatabase

    override val isOpen: Boolean
        get() = delegate.isOpen

    override val isReadOnly: Boolean
        get() = delegate.isReadOnly

    override val path: String?
        get() = delegate.path

    override var version: Int
        get() = delegate.version
        set(value) { delegate.version = value }

    override val maximumSize: Long
        get() = delegate.maximumSize

    override var pageSize: Long
        get() = delegate.pageSize
        set(value) { delegate.pageSize = value }

    override val isDbLockedByCurrentThread: Boolean
        get() = delegate.isDbLockedByCurrentThread

    override val attachedDbs: List<Pair<String, String>>?
        get() = null // TODO: implement when needed

    override val isDatabaseIntegrityOk: Boolean
        get() = delegate.isDatabaseIntegrityOk

    override val isWriteAheadLoggingEnabled: Boolean
        get() = delegate.isWriteAheadLoggingEnabled

    override fun close() {
        delegate.close()
    }

    override fun compileStatement(sql: String): SupportSQLiteStatement {
        return LiteSyncStatement(delegate.compileStatement(sql))
    }

    override fun beginTransaction() {
        delegate.beginTransaction()
    }

    override fun beginTransactionNonExclusive() {
        delegate.beginTransactionNonExclusive()
    }

    override fun beginTransactionWithListener(transactionListener: android.database.sqlite.SQLiteTransactionListener) {
        delegate.beginTransactionWithListener(object : org.sqlite.database.sqlite.SQLiteTransactionListener {
            override fun onBegin() = transactionListener.onBegin()
            override fun onCommit() = transactionListener.onCommit()
            override fun onRollback() = transactionListener.onRollback()
        })
    }

    override fun beginTransactionWithListenerNonExclusive(transactionListener: android.database.sqlite.SQLiteTransactionListener) {
        delegate.beginTransactionWithListenerNonExclusive(object : org.sqlite.database.sqlite.SQLiteTransactionListener {
            override fun onBegin() = transactionListener.onBegin()
            override fun onCommit() = transactionListener.onCommit()
            override fun onRollback() = transactionListener.onRollback()
        })
    }

    override fun endTransaction() {
        delegate.endTransaction()
    }

    override fun setTransactionSuccessful() {
        delegate.setTransactionSuccessful()
    }

    override fun inTransaction(): Boolean {
        return delegate.inTransaction()
    }

    override fun yieldIfContendedSafely(): Boolean {
        return delegate.yieldIfContendedSafely()
    }

    override fun yieldIfContendedSafely(sleepAfterYieldDelayMillis: Long): Boolean {
        return delegate.yieldIfContendedSafely(sleepAfterYieldDelayMillis)
    }

    override fun setForeignKeyConstraintsEnabled(enabled: Boolean) {
        delegate.setForeignKeyConstraintsEnabled(enabled)
    }

    override fun enableWriteAheadLogging(): Boolean {
        return delegate.enableWriteAheadLogging()
    }

    override fun disableWriteAheadLogging() {
        delegate.disableWriteAheadLogging()
    }

    override fun setMaximumSize(numBytes: Long): Long {
        return delegate.setMaximumSize(numBytes)
    }

    override fun query(query: String): Cursor {
        return delegate.rawQuery(query, null)
    }

    override fun query(query: String, bindArgs: Array<out Any?>): Cursor {
        return delegate.rawQuery(query, bindArgs.map { it?.toString() }.toTypedArray())
    }

    override fun query(query: SupportSQLiteQuery): Cursor {
        return query(query, null)
    }

    override fun query(
        query: SupportSQLiteQuery,
        cancellationSignal: android.os.CancellationSignal?
    ): Cursor {
        val args = mutableListOf<Any?>()
        query.bindTo(object : androidx.sqlite.db.SupportSQLiteProgram {
            override fun bindNull(index: Int) { args.add(null) }
            override fun bindLong(index: Int, value: Long) { args.add(value) }
            override fun bindDouble(index: Int, value: Double) { args.add(value) }
            override fun bindString(index: Int, value: String) { args.add(value) }
            override fun bindBlob(index: Int, value: ByteArray) { args.add(value) }
            override fun clearBindings() { args.clear() }
            override fun close() {}
        })
        return delegate.rawQuery(query.sql, args.map { it?.toString() }.toTypedArray())
    }

    override fun insert(table: String, conflictAlgorithm: Int, values: android.content.ContentValues): Long {
        return delegate.insertWithOnConflict(table, null, values, conflictAlgorithm)
    }

    override fun delete(table: String, whereClause: String?, whereArgs: Array<out Any?>?): Int {
        return delegate.delete(table, whereClause, whereArgs?.map { it?.toString() }?.toTypedArray())
    }

    override fun update(
        table: String,
        conflictAlgorithm: Int,
        values: android.content.ContentValues,
        whereClause: String?,
        whereArgs: Array<out Any?>?
    ): Int {
        return delegate.updateWithOnConflict(
            table,
            values,
            whereClause,
            whereArgs?.map { it?.toString() }?.toTypedArray(),
            conflictAlgorithm
        )
    }

    override fun execSQL(sql: String) {
        delegate.execSQL(sql)
    }

    override fun execSQL(sql: String, bindArgs: Array<out Any?>) {
        delegate.execSQL(sql, bindArgs)
    }

    override fun needUpgrade(newVersion: Int): Boolean {
        return delegate.needUpgrade(newVersion)
    }

    override fun setLocale(locale: java.util.Locale) {
        delegate.setLocale(locale)
    }

    override fun setMaxSqlCacheSize(cacheSize: Int) {
        delegate.setMaxSqlCacheSize(cacheSize)
    }
}

/**
 * Wraps LiteSync's SQLiteStatement to implement [SupportSQLiteStatement].
 */
private class LiteSyncStatement(
    private val delegate: org.sqlite.database.sqlite.SQLiteStatement
) : SupportSQLiteStatement {

    override fun execute() {
        delegate.execute()
    }

    override fun executeUpdateDelete(): Int {
        return delegate.executeUpdateDelete()
    }

    override fun executeInsert(): Long {
        return delegate.executeInsert()
    }

    override fun simpleQueryForLong(): Long {
        return delegate.simpleQueryForLong()
    }

    override fun simpleQueryForString(): String? {
        return delegate.simpleQueryForString()
    }

    override fun bindNull(index: Int) {
        delegate.bindNull(index)
    }

    override fun bindLong(index: Int, value: Long) {
        delegate.bindLong(index, value)
    }

    override fun bindDouble(index: Int, value: Double) {
        delegate.bindDouble(index, value)
    }

    override fun bindString(index: Int, value: String) {
        delegate.bindString(index, value)
    }

    override fun bindBlob(index: Int, value: ByteArray) {
        delegate.bindBlob(index, value)
    }

    override fun clearBindings() {
        delegate.clearBindings()
    }

    override fun close() {
        delegate.close()
    }
}
