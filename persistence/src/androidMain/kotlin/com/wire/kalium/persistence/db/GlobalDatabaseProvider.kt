package com.wire.kalium.persistence.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.wire.kalium.persistence.BuildConfig
import com.wire.kalium.persistence.DBUtil
import com.wire.kalium.persistence.GlobalDatabase
import com.wire.kalium.persistence.ServerConfiguration
import com.wire.kalium.persistence.dao_kalium_db.ServerConfigurationDAO
import com.wire.kalium.persistence.dao_kalium_db.ServerConfigurationDAOImpl
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import com.wire.kalium.persistence.util.FileNameUtil
import net.sqlcipher.database.SupportFactory

actual class GlobalDatabaseProvider(private val context: Context, kaliumPreferences: KaliumPreferences, encrypt: Boolean = true) {
    private val dbName = FileNameUtil.globalDBName()
    private val driver: AndroidSqliteDriver
    private val database: GlobalDatabase


    init {
        val onConnectCallback = object : AndroidSqliteDriver.Callback(GlobalDatabase.Schema) {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                db.execSQL("PRAGMA foreign_keys=ON;")
            }
        }
        driver = if (encrypt) {
            AndroidSqliteDriver(
                schema = GlobalDatabase.Schema,
                context = context,
                name = dbName,
                factory = SupportFactory(
                    DBUtil.getOrGenerateSecretKey(kaliumPreferences, DATABASE_SECRET_KEY).toByteArray()
                ),
                callback = onConnectCallback
            )
        } else {
            AndroidSqliteDriver(
                schema = GlobalDatabase.Schema,
                context = context,
                name = dbName,
                callback = onConnectCallback
            )

        }


        database = GlobalDatabase(
            driver, ServerConfiguration.Adapter(
                commonApiVersionAdapter = IntColumnAdapter
            )
        )
    }

    actual val serverConfigurationDAO: ServerConfigurationDAO
        get() = ServerConfigurationDAOImpl(database.serverConfigurationQueries)

    actual fun nuke(): Boolean = DBUtil.deleteDB(driver, context, dbName)

    companion object {
        private const val DATABASE_SECRET_KEY = "global-db-secret"
    }


}
