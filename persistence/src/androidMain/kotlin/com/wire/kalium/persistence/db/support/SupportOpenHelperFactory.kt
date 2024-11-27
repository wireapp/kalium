package com.wire.kalium.persistence.db.support

import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SQLiteConnection
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook
import net.zetetic.database.sqlcipher.SQLiteOpenHelper

class SupportOpenHelperFactory(
    private val password: ByteArray?,
    private val enableWriteAheadLogging: Boolean = false,
    private val hook: SQLiteDatabaseHook? = object : SQLiteDatabaseHook {
        override fun preKey(connection: SQLiteConnection?) {
            connection?.executeRaw("PRAGMA cipher_profile = device;", emptyArray(), null)
        }

        override fun postKey(connection: SQLiteConnection?) {
            // nop
        }
    },
    private val minimumSupportedDatabaseVersion: Int = 1
) : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper =
        object : SQLiteOpenHelper(
            configuration.context,
            configuration.name,
            password,
            null,
            configuration.callback.version,
            minimumSupportedDatabaseVersion,
            null,
            hook,
            enableWriteAheadLogging
        ) {
            override fun onCreate(db: SQLiteDatabase) {
                configuration.callback.onCreate(db)
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
                configuration.callback.onUpgrade(db, oldVersion, newVersion)
            }

            override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
                configuration.callback.onDowngrade(db, oldVersion, newVersion)
            }

            override fun onOpen(db: SQLiteDatabase) {
                configuration.callback.onOpen(db)
            }

            override fun onConfigure(db: SQLiteDatabase) {
                configuration.callback.onConfigure(db)
            }
        }
}
