package com.wire.kalium.persistence.db

import app.cash.sqldelight.db.SqlDriver
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.backup.BackupImporter
import com.wire.kalium.persistence.backup.BackupImporterImpl
import com.wire.kalium.persistence.cache.LRUCache
import com.wire.kalium.persistence.dao.ConnectionDAO
import com.wire.kalium.persistence.dao.ConnectionDAOImpl
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.ConversationDAOImpl
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.MetadataDAOImpl
import com.wire.kalium.persistence.dao.PrekeyDAO
import com.wire.kalium.persistence.dao.PrekeyDAOImpl
import com.wire.kalium.persistence.dao.TeamDAO
import com.wire.kalium.persistence.dao.TeamDAOImpl
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserDAOImpl
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.asset.AssetDAO
import com.wire.kalium.persistence.dao.asset.AssetDAOImpl
import com.wire.kalium.persistence.dao.call.CallDAO
import com.wire.kalium.persistence.dao.call.CallDAOImpl
import com.wire.kalium.persistence.dao.client.ClientDAO
import com.wire.kalium.persistence.dao.client.ClientDAOImpl
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageDAOImpl
import com.wire.kalium.persistence.dao.reaction.ReactionDAO
import com.wire.kalium.persistence.dao.reaction.ReactionDAOImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlin.jvm.JvmInline

internal const val USER_CACHE_SIZE = 125
internal const val METADATA_CACHE_SIZE = 30

@JvmInline
value class UserDBSecret(val value: ByteArray)

/**
 * Platform-specific data used to create the database
 * that might be necessary for future operations
 * in the future like [nuke]
 */
internal expect class PlatformDatabaseData

class UserDatabaseBuilder internal constructor(
    private val userId: UserIDEntity,
    private val sqlDriver: SqlDriver,
    dispatcher: CoroutineDispatcher,
    private val platformDatabaseData: PlatformDatabaseData
) {

    internal val database: UserDatabase = UserDatabase(
        driver = sqlDriver,
        CallAdapter = TableMapper.callAdapter,
        ClientAdapter = TableMapper.clientAdapter,
        ConnectionAdapter = TableMapper.connectionAdapter,
        ConversationAdapter = TableMapper.conversationAdapter,
        MemberAdapter = TableMapper.memberAdapter,
        MessageAdapter = TableMapper.messageAdapter,
        MessageAssetContentAdapter = TableMapper.messageAssetContentAdapter,
        MessageConversationChangedContentAdapter = TableMapper.messageConversationChangedContentAdapter,
        MessageFailedToDecryptContentAdapter = TableMapper.messageFailedToDecryptContentAdapter,
        MessageMemberChangeContentAdapter = TableMapper.messageMemberChangeContentAdapter,
        MessageMentionAdapter = TableMapper.messageMentionAdapter,
        MessageMissedCallContentAdapter = TableMapper.messageMissedCallContentAdapter,
        MessageRestrictedAssetContentAdapter = TableMapper.messageRestrictedAssetContentAdapter,
        MessageTextContentAdapter = TableMapper.messageTextContentAdapter,
        MessageUnknownContentAdapter = TableMapper.messageUnknownContentAdapter,
        ReactionAdapter = TableMapper.reactionAdapter,
        SelfUserAdapter = TableMapper.selfUserAdapter,
        UserAdapter = TableMapper.userAdapter
    )

    init {
        database.databasePropertiesQueries.insertSelfUserId(userId)
        database.databasePropertiesQueries.enableForeignKeyContraints()
    }

    private val databaseScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val userCache = LRUCache<UserIDEntity, Flow<UserEntity?>>(USER_CACHE_SIZE)
    val userDAO: UserDAO
        get() = UserDAOImpl(database.usersQueries, userCache, databaseScope)

    val connectionDAO: ConnectionDAO
        get() = ConnectionDAOImpl(database.connectionsQueries, database.conversationsQueries)

    val conversationDAO: ConversationDAO
        get() = ConversationDAOImpl(database.conversationsQueries, database.usersQueries, database.membersQueries)

    private val metadataCache = LRUCache<String, Flow<String?>>(METADATA_CACHE_SIZE)
    val metadataDAO: MetadataDAO
        get() = MetadataDAOImpl(database.metadataQueries, metadataCache, databaseScope)

    val clientDAO: ClientDAO
        get() = ClientDAOImpl(database.clientsQueries)

    val backupImporter: BackupImporter
        get() = BackupImporterImpl(sqlDriver)

    val callDAO: CallDAO
        get() = CallDAOImpl(database.callsQueries)

    val messageDAO: MessageDAO
        get() = MessageDAOImpl(database.messagesQueries, database.conversationsQueries, userId)

    val assetDAO: AssetDAO
        get() = AssetDAOImpl(database.assetsQueries)

    val teamDAO: TeamDAO
        get() = TeamDAOImpl(database.teamsQueries)

    val reactionDAO: ReactionDAO
        get() = ReactionDAOImpl(database.reactionsQueries)

    val prekeyDAO: PrekeyDAO
        get() = PrekeyDAOImpl(database.metadataQueries)

    /**
     * drops DB connection and delete the DB file
     */
    fun nuke(): Boolean {
        sqlDriver.close()
        databaseScope.cancel()
        return nuke(userId, database, platformDatabaseData)
    }
}

internal expect fun nuke(
    userId: UserIDEntity,
    database: UserDatabase,
    platformDatabaseData: PlatformDatabaseData
): Boolean
