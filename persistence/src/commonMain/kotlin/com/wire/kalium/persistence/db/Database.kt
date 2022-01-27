package com.wire.kalium.persistence.db

import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.ConversationDAO

expect class Database {
    val userDAO: UserDAO
    val conversationDAO: ConversationDAO
}
