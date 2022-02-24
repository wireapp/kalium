package com.wire.kalium.persistence.db

import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.client.ClientDAO
import com.wire.kalium.persistence.dao.message.MessageDAO

actual class Database {
    actual val userDAO: UserDAO
        get() = TODO("Not yet implemented")
    actual val conversationDAO: ConversationDAO
        get() = TODO("Not yet implemented")
    actual val metadataDAO: MetadataDAO
        get() = TODO("Not yet implemented")
    actual val clientDAO: ClientDAO
        get() = TODO("Not yet implemented")
    actual val messageDAO: MessageDAO
        get() = TODO("Not yet implemented")

    actual fun nuke(): Boolean {
        TODO("Not yet implemented")
    }
}
