package com.wire.kalium.persistence.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.wire.kalium.persistence.DBUtil
import com.wire.kalium.persistence.KaliumDatabase
import com.wire.kalium.persistence.dao_kalium_db.ServerConfigurationDAO
import com.wire.kalium.persistence.dao_kalium_db.ServerConfigurationDAOImpl
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import com.wire.kalium.persistence.util.FileNameUtil
import net.sqlcipher.database.SupportFactory

actual class KaliumDatabaseProvider(private val context: Context, kaliumPreferences: KaliumPreferences) {
    private val dbName = FileNameUtil.appDBName()
    private val driver: AndroidSqliteDriver
    private val database: KaliumDatabase


    init {
        val supportFactory = SupportFactory(DBUtil.getOrGenerateSecretKey(kaliumPreferences, DATABASE_SECRET_KEY).toByteArray())

        val onConnectCallback = object : AndroidSqliteDriver.Callback(KaliumDatabase.Schema) {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                db.execSQL("PRAGMA foreign_keys=ON;")
            }
        }
        driver = AndroidSqliteDriver(
            schema = KaliumDatabase.Schema,
            context = context,
            name = dbName,
            factory = supportFactory,
            callback = onConnectCallback
        )
        database = KaliumDatabase(driver)
    }

    actual val serverConfigurationDAO: ServerConfigurationDAO
        get() = ServerConfigurationDAOImpl(database.serverConfigurationQueries)

    actual fun nuke(): Boolean = DBUtil.deleteDB(driver, context, dbName)

    companion object {
        private const val DATABASE_SECRET_KEY = "kalium-db-secret"
    }


}
