package com.wire.kalium.persistence.db

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.ConversationDAOImpl
import com.wire.kalium.persistence.dao.QualifiedIDAdapter
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserDAOImpl
import net.sqlcipher.database.SupportFactory

actual class Database(context: Context, name: String, passphrase: String) {

    val database: AppDatabase

    init {
        val supportFactory = SupportFactory(passphrase.toByteArray())
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
}
