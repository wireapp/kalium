package com.wire.kalium.persistence

import android.content.Context
import android.os.Build
import android.util.Base64
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import java.security.SecureRandom

object DBUtil {
    fun getOrGenerateSecretKey(kaliumPreferences: KaliumPreferences, prefKey: String): String {
        val databaseKey = kaliumPreferences.getString(prefKey)

        return if (databaseKey == null) {
            val secretKey = generateSecretKey()
            kaliumPreferences.putString(prefKey, secretKey)
            secretKey
        } else {
            databaseKey
        }
    }

    private fun generateSecretKey(): String {
        // TODO review with security

        val random = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            SecureRandom.getInstanceStrong()
        } else {
            SecureRandom()
        }
        val password = ByteArray(DATABASE_SECRET_LENGTH)
        random.nextBytes(password)

        return Base64.encodeToString(password, Base64.DEFAULT)
    }

    private const val DATABASE_SECRET_LENGTH = 48


    fun deleteDB(driver: AndroidSqliteDriver, context: Context, dbName: String): Boolean {
        driver.close()
        return context.deleteDatabase(dbName)
    }
}
