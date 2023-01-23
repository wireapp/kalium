package com.wire.kalium.persistence.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

internal class SqliteCallback(schema: SqlSchema) : SupportSQLiteOpenHelper.Callback(schema.version) {
    private val baseCallback = AndroidSqliteDriver.Callback(schema)
    override fun onCreate(db: SupportSQLiteDatabase) = baseCallback.onCreate(db)

    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
        baseCallback.onUpgrade(
            db,
            oldVersion,
            newVersion
        )

    override fun onConfigure(db: SupportSQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
    }
}
