package com.wire.kalium.persistence.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.ConversationDAOImpl
import com.wire.kalium.persistence.dao.QualifiedIDAdapter
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserDAOImpl
import com.wire.kalium.persistence.dao.client.ClientDAO
import com.wire.kalium.persistence.dao.client.ClientDAOImpl
import net.sqlcipher.database.SupportFactory

actual class Database(context: Context, name: String, passphrase: String) {

    val database: AppDatabase

    init {
        val supportFactory = SupportFactory(passphrase.toByteArray())

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
            User.Adapter(qualified_idAdapter = QualifiedIDAdapter())
        )
    }

    actual val userDAO: UserDAO
        get() = UserDAOImpl(database.usersQueries)

    actual val conversationDAO: ConversationDAO
        get() = ConversationDAOImpl(database.converationsQueries, database.membersQueries)

    actual val clientDAO: ClientDAO
        get() = ClientDAOImpl(database.clientsQueries)
}
