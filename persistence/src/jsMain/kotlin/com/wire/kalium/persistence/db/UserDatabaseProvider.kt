package com.wire.kalium.persistence.db

import com.wire.kalium.persistence.dao.ConnectionDAO
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.TeamDAO
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.asset.AssetDAO
import com.wire.kalium.persistence.dao.call.CallDAO
import com.wire.kalium.persistence.dao.client.ClientDAO
import com.wire.kalium.persistence.dao.message.MessageDAO

actual class UserDatabaseProvider {
    actual val userDAO: UserDAO
        get() = TODO("Not yet implemented")
    actual val conversationDAO: ConversationDAO
        get() = TODO("Not yet implemented")
    actual val metadataDAO: MetadataDAO
        get() = TODO("Not yet implemented")
    actual val clientDAO: ClientDAO
        get() = TODO("Not yet implemented")
    actual val callDAO: CallDAO
        get() = TODO("Not yet implemented")
    actual val messageDAO: MessageDAO
        get() = TODO("Not yet implemented")
    actual val assetDAO: AssetDAO
        get() = TODO("Not yet implemented")
    actual val teamDAO: TeamDAO
        get() = TODO("Not yet implemented")

    actual fun nuke(): Boolean {
        TODO("Not yet implemented")
    }

    actual val connectionDAO: ConnectionDAO
        get() = TODO("Not yet implemented")
}
