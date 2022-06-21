package com.wire.kalium.persistence.db

import com.wire.kalium.persistence.dao.ConnectionDAO
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.TeamDAO
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.asset.AssetDAO
import com.wire.kalium.persistence.dao.client.ClientDAO
import com.wire.kalium.persistence.dao.message.MessageDAO
import kotlin.jvm.JvmInline

@JvmInline
value class UserDBSecret(val value: ByteArray)

expect class UserDatabaseProvider {
    val userDAO: UserDAO
    val connectionDAO: ConnectionDAO
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
