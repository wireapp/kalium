package com.wire.kalium.persistence.db

import android.content.Context
import android.os.Build
import android.util.Base64
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.ConversationDAOImpl
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.MetadataDAOImpl
import com.wire.kalium.persistence.dao.QualifiedIDAdapter
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserDAOImpl
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import net.sqlcipher.database.SupportFactory
import java.security.SecureRandom

actual class Database(context: Context, name: String, kaliumPreferences: KaliumPreferences) {

    val database: AppDatabase

    init {
        val supportFactory = SupportFactory(getOrGenerateSecretKey(kaliumPreferences).toByteArray())
        val driver =  AndroidSqliteDriver(AppDatabase.Schema, context, name, factory = supportFactory)

        database = AppDatabase(
            driver,
            Conversation.Adapter(qualified_idAdapter = QualifiedIDAdapter()),
            Member.Adapter(userAdapter = QualifiedIDAdapter(), conversationAdapter = QualifiedIDAdapter()),
            User.Adapter(qualified_idAdapter = QualifiedIDAdapter()))
    }

    actual val userDAO: UserDAO
        get() = UserDAOImpl(database.usersQueries)

    actual val conversationDAO: ConversationDAO
        get() = ConversationDAOImpl(database.converationsQueries, database.membersQueries)

    actual val metadataDAO: MetadataDAO
        get() = MetadataDAOImpl(database.metadataQueries)
    }

    private fun getOrGenerateSecretKey(kaliumPreferences: KaliumPreferences): String {
        val databaseKey = kaliumPreferences.getString(DATABASE_SECRET_KEY)

        return if (databaseKey == null) {
            val secretKey = generateSecretKey()
            kaliumPreferences.putString(DATABASE_SECRET_KEY, secretKey)
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

    companion object {
        private const val DATABASE_SECRET_KEY = "databaseSecret"
        private const val DATABASE_SECRET_LENGTH = 48
    }

}
