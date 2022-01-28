package com.wire.kalium.persistence.db

import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.UserDAO

actual class Database {
    actual val userDAO: UserDAO
        get() = TODO("Not yet implemented")
    actual val conversationDAO: ConversationDAO
        get() = TODO("Not yet implemented")
}
