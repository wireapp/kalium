package com.wire.kalium.persistence.db

import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.client.ClientDAO

expect class Database {
    val userDAO: UserDAO
    val conversationDAO: ConversationDAO
    val clientDAO: ClientDAO
}
