package com.wire.kalium.persistence.db

import android.content.Context
import android.os.Build
import android.util.Base64
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.ConversationDAOImpl
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.MetadataDAOImpl
import com.wire.kalium.persistence.dao.QualifiedIDAdapter
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserDAOImpl
import com.wire.kalium.persistence.dao.client.ClientDAO
import com.wire.kalium.persistence.dao.client.ClientDAOImpl
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageDAOImpl
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import net.sqlcipher.database.SupportFactory
import java.security.SecureRandom

actual class Database(context: Context, name: String, kaliumPreferences: KaliumPreferences) {

    val database: AppDatabase

    init {
        val supportFactory = SupportFactory(getOrGenerateSecretKey(kaliumPreferences).toByteArray())

        val onConnectCallback = object : AndroidSqliteDriver.Callback(AppDatabase.Schema) {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                db.execSQL("PRAGMA foreign_keys=ON;")
            }
        }

        val driver = AndroidSqliteDriver(
            schema = AppDatabase.Schema,
            context = context,
            name = name,
            factory = supportFactory,
            callback = onConnectCallback
        )

        database = AppDatabase(
            driver,
            Client.Adapter(user_idAdapter = QualifiedIDAdapter()),
            Conversation.Adapter(qualified_idAdapter = QualifiedIDAdapter()),
            Member.Adapter(userAdapter = QualifiedIDAdapter(), conversationAdapter = QualifiedIDAdapter()),
            Message.Adapter(
                conversation_idAdapter = QualifiedIDAdapter(),
                sender_user_idAdapter = QualifiedIDAdapter(),
                statusAdapter = EnumColumnAdapter()
            ),
            User.Adapter(qualified_idAdapter = QualifiedIDAdapter(), IntColumnAdapter)
        )
    }

    actual val userDAO: UserDAO
        get() = UserDAOImpl(database.usersQueries)

    actual val conversationDAO: ConversationDAO
        get() = ConversationDAOImpl(database.converationsQueries, database.usersQueries, database.membersQueries)

    actual val metadataDAO: MetadataDAO
        get() = MetadataDAOImpl(database.metadataQueries)

    actual val clientDAO: ClientDAO
        get() = ClientDAOImpl(database.clientsQueries)

    actual val messageDAO: MessageDAO
        get() = MessageDAOImpl(database.messagesQueries)

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
