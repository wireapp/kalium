package com.wire.kalium.persistence.db

import android.content.Context
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.db.SqlDriver
import net.sqlcipher.database.SupportFactory

actual class DatabaseDriverFactory(private val context: Context, private val passphrase: String) {

    actual suspend fun createDriver(): SqlDriver {
        val factory = SupportFactory(passphrase.toByteArray())
        return AndroidSqliteDriver(AppDatabase.Schema, context, "main.db", factory = factory)
    }

}
