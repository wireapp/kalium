package com.wire.kalium.persistence.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.wire.kalium.persistence.Call
import com.wire.kalium.persistence.Client
import com.wire.kalium.persistence.Connection
import com.wire.kalium.persistence.Conversation
import com.wire.kalium.persistence.Member
import com.wire.kalium.persistence.Message
import com.wire.kalium.persistence.MessageAssetContent
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
import kotlinx.coroutines.cancel
import net.sqlcipher.database.SupportFactory

actual class UserDatabaseProvider(
    private val context: Context,
    userId: UserIDEntity,
    passphrase: UserDBSecret,
    encrypt: Boolean = true,
    dispatcher: CoroutineDispatcher
) {
    private val dbName = FileNameUtil.userDBName(userId)
    private val driver: AndroidSqliteDriver
    internal val database: UserDatabase

    init {
        val onConnectCallback = object : AndroidSqliteDriver.Callback(UserDatabase.Schema) {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                db.execSQL("PRAGMA foreign_keys=ON;")
            }
        }

        driver = if (encrypt) {
            AndroidSqliteDriver(
                schema = UserDatabase.Schema,
                context = context,
                name = dbName,
                factory = SupportFactory(passphrase.value),
                callback = onConnectCallback
            )
        } else {
            AndroidSqliteDriver(
                schema = UserDatabase.Schema,
                context = context,
                name = dbName,
                callback = onConnectCallback
            )
        }

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
                asset_download_statusAdapter = EnumColumnAdapter()
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
    }

    private val databaseScope = CoroutineScope(SupervisorJob() + dispatcher)

    actual val userDAO: UserDAO
        get() = UserDAOImpl(database.usersQueries, LRUCache(USER_CACHE_SIZE), databaseScope)

    actual val connectionDAO: ConnectionDAO
        get() = ConnectionDAOImpl(database.connectionsQueries, database.conversationsQueries)

    actual val conversationDAO: ConversationDAO
        get() = ConversationDAOImpl(database.conversationsQueries, database.usersQueries, database.membersQueries)

    actual val metadataDAO: MetadataDAO
        get() = MetadataDAOImpl(database.metadataQueries)

    actual val clientDAO: ClientDAO
        get() = ClientDAOImpl(database.clientsQueries)

    actual val callDAO: CallDAO
        get() = CallDAOImpl(database.callsQueries)

    actual val messageDAO: MessageDAO
        get() = MessageDAOImpl(database.messagesQueries)

    actual val assetDAO: AssetDAO
        get() = AssetDAOImpl(database.assetsQueries)

    actual val teamDAO: TeamDAO
        get() = TeamDAOImpl(database.teamsQueries)

    actual fun nuke(): Boolean {
        databaseScope.cancel()
        driver.close()
        return context.deleteDatabase(dbName)
    }
}
