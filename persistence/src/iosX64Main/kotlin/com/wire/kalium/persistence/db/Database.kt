package com.wire.kalium.persistence.db

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.ConversationDAOImpl
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.MetadataDAOImpl
import com.wire.kalium.persistence.dao.QualifiedIDAdapter
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserDAOImpl

actual class Database(name: String, passphrase: String) {

    val database: AppDatabase

    init {
        val driver = NativeSqliteDriver(AppDatabase.Schema, name)
        database = AppDatabase(driver,
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
