package com.wire.kalium.persistence.db

import com.wire.kalium.persistence.dao.asset.AssetDAO
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.client.ClientDAO
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.TeamDAO
import com.wire.kalium.persistence.dao.message.MessageDAO

expect class Database {
    val userDAO: UserDAO
    val conversationDAO: ConversationDAO
    val metadataDAO: MetadataDAO
    val messageDAO: MessageDAO
    val clientDAO: ClientDAO
    val assetDAO: AssetDAO
    val teamDAO: TeamDAO

    /**
     * drops DB connection and delete the DB file
     */
    fun nuke(): Boolean
}
