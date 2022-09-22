package com.wire.kalium.persistence.db

import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.wire.kalium.persistence.Call
import com.wire.kalium.persistence.Client
import com.wire.kalium.persistence.Connection
import com.wire.kalium.persistence.Conversation
import com.wire.kalium.persistence.Member
import com.wire.kalium.persistence.Message
import com.wire.kalium.persistence.MessageAssetContent
import com.wire.kalium.persistence.MessageConversationChangedContent
import com.wire.kalium.persistence.MessageFailedToDecryptContent
import com.wire.kalium.persistence.MessageMemberChangeContent
import com.wire.kalium.persistence.MessageMention
import com.wire.kalium.persistence.MessageMissedCallContent
import com.wire.kalium.persistence.MessageRestrictedAssetContent
import com.wire.kalium.persistence.MessageTextContent
import com.wire.kalium.persistence.MessageUnknownContent
import com.wire.kalium.persistence.User
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.cache.LRUCache
import com.wire.kalium.persistence.dao.BotServiceAdapter
import com.wire.kalium.persistence.dao.ConnectionDAO
import com.wire.kalium.persistence.dao.ConnectionDAOImpl
import com.wire.kalium.persistence.dao.ContentTypeAdapter
import com.wire.kalium.persistence.dao.ConversationAccessListAdapter
import com.wire.kalium.persistence.dao.ConversationAccessRoleListAdapter
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.ConversationDAOImpl
import com.wire.kalium.persistence.dao.MemberRoleAdapter
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.MetadataDAOImpl
import com.wire.kalium.persistence.dao.QualifiedIDAdapter
import com.wire.kalium.persistence.dao.QualifiedIDListAdapter
import com.wire.kalium.persistence.dao.TeamDAO
import com.wire.kalium.persistence.dao.TeamDAOImpl
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserDAOImpl
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.asset.AssetDAO
import com.wire.kalium.persistence.dao.asset.AssetDAOImpl
import com.wire.kalium.persistence.dao.call.CallDAO
import com.wire.kalium.persistence.dao.call.CallDAOImpl
import com.wire.kalium.persistence.dao.client.ClientDAO
import com.wire.kalium.persistence.dao.client.ClientDAOImpl
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageDAOImpl
import com.wire.kalium.persistence.util.FileNameUtil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

actual class UserDatabaseProvider(
    userId: UserIDEntity,
    passphrase: String,
    dispatcher: CoroutineDispatcher
) {

    val database: UserDatabase

    init {
        val driver = NativeSqliteDriver(UserDatabase.Schema, FileNameUtil.userDBName(userId))
        database = UserDatabase(
            driver,
            Call.Adapter(
                conversation_idAdapter = QualifiedIDAdapter,
                statusAdapter = EnumColumnAdapter(),
                conversation_typeAdapter = EnumColumnAdapter()
            ),
            Client.Adapter(
                user_idAdapter = QualifiedIDAdapter,
                device_typeAdapter = EnumColumnAdapter()
            ),
            Connection.Adapter(
                qualified_conversationAdapter = QualifiedIDAdapter,
                qualified_toAdapter = QualifiedIDAdapter,
                statusAdapter = EnumColumnAdapter()
            ),
            Conversation.Adapter(
                qualified_idAdapter = QualifiedIDAdapter,
                typeAdapter = EnumColumnAdapter(),
                mls_group_stateAdapter = EnumColumnAdapter(),
                protocolAdapter = EnumColumnAdapter(),
                muted_statusAdapter = EnumColumnAdapter(),
                access_listAdapter = ConversationAccessListAdapter(),
                access_role_listAdapter = ConversationAccessRoleListAdapter(),
                mls_cipher_suiteAdapter = EnumColumnAdapter()
            ),
            Member.Adapter(
                userAdapter = QualifiedIDAdapter,
                conversationAdapter = QualifiedIDAdapter,
                roleAdapter = MemberRoleAdapter()
            ),
            Message.Adapter(
                conversation_idAdapter = QualifiedIDAdapter,
                sender_user_idAdapter = QualifiedIDAdapter,
                statusAdapter = EnumColumnAdapter(),
                content_typeAdapter = ContentTypeAdapter(),
                visibilityAdapter = EnumColumnAdapter(),
            ),
            MessageAssetContent.Adapter(
                conversation_idAdapter = QualifiedIDAdapter,
                asset_widthAdapter = IntColumnAdapter,
                asset_heightAdapter = IntColumnAdapter,
                asset_upload_statusAdapter = EnumColumnAdapter(),
                asset_download_statusAdapter = EnumColumnAdapter(),
            ),
            MessageConversationChangedContent.Adapter(
                conversation_idAdapter = QualifiedIDAdapter
            ),
            MessageFailedToDecryptContent.Adapter(
                conversation_idAdapter = QualifiedIDAdapter
            ),
            MessageMemberChangeContent.Adapter(
                conversation_idAdapter = QualifiedIDAdapter,
                member_change_listAdapter = QualifiedIDListAdapter(),
                member_change_typeAdapter = EnumColumnAdapter()
            ),
            MessageMention.Adapter(
                conversation_idAdapter = QualifiedIDAdapter,
                user_idAdapter = QualifiedIDAdapter,
                startAdapter = IntColumnAdapter,
                lengthAdapter = IntColumnAdapter
            ),
            MessageMissedCallContent.Adapter(
                conversation_idAdapter = QualifiedIDAdapter,
                caller_idAdapter = QualifiedIDAdapter
            ),
            MessageRestrictedAssetContent.Adapter(
                conversation_idAdapter = QualifiedIDAdapter
            ),
            MessageTextContent.Adapter(
                conversation_idAdapter = QualifiedIDAdapter
            ),
            MessageUnknownContent.Adapter(
                conversation_idAdapter = QualifiedIDAdapter
            ),
            User.Adapter(
                qualified_idAdapter = QualifiedIDAdapter,
                accent_idAdapter = IntColumnAdapter,
                connection_statusAdapter = EnumColumnAdapter(),
                user_availability_statusAdapter = EnumColumnAdapter(),
                preview_asset_idAdapter = QualifiedIDAdapter,
                complete_asset_idAdapter = QualifiedIDAdapter,
                user_typeAdapter = EnumColumnAdapter(),
                bot_serviceAdapter = BotServiceAdapter()
            )
        )
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
    }

    private val databaseScope = CoroutineScope(SupervisorJob() + dispatcher)
    actual val userDAO: UserDAO
        get() = UserDAOImpl(database.usersQueries, LRUCache(USER_CACHE_SIZE), databaseScope)

    actual val conversationDAO: ConversationDAO
        get() = ConversationDAOImpl(database.conversationsQueries, database.usersQueries, database.membersQueries)

    actual val metadataDAO: MetadataDAO
        get() = MetadataDAOImpl(database.metadataQueries, LRUCache(METADATA_CACHE_SIZE), databaseScope)

    actual val clientDAO: ClientDAO
        get() = ClientDAOImpl(database.clientsQueries)

    actual val callDAO: CallDAO
        get() = CallDAOImpl(database.callsQueries)

    actual val messageDAO: MessageDAO
        get() = MessageDAOImpl(database.messagesQueries, database.conversationsQueries)

    actual val assetDAO: AssetDAO
        get() = AssetDAOImpl(database.assetsQueries)

    actual val teamDAO: TeamDAO
        get() = TeamDAOImpl(database.teamsQueries)

    actual fun nuke(): Boolean {
        TODO("Not yet implemented")
    }

    actual val connectionDAO: ConnectionDAO
        get() = ConnectionDAOImpl(database.connectionsQueries, database.conversationsQueries)
}
