package com.wire.kalium.persistence.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.wire.kalium.persistence.Client
import com.wire.kalium.persistence.Conversation
import com.wire.kalium.persistence.DBUtil
import com.wire.kalium.persistence.Member
import com.wire.kalium.persistence.Message
import com.wire.kalium.persistence.User
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.dao.ContentTypeAdapter
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.ConversationDAOImpl
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.MetadataDAOImpl
import com.wire.kalium.persistence.dao.QualifiedIDAdapter
import com.wire.kalium.persistence.dao.TeamDAO
import com.wire.kalium.persistence.dao.TeamDAOImpl
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserDAOImpl
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.asset.AssetDAO
import com.wire.kalium.persistence.dao.asset.AssetDAOImpl
import com.wire.kalium.persistence.dao.client.ClientDAO
import com.wire.kalium.persistence.dao.client.ClientDAOImpl
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageDAOImpl
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import com.wire.kalium.persistence.util.FileNameUtil
import net.sqlcipher.database.SupportFactory

actual class UserDatabaseProvider(private val context: Context, userId: UserIDEntity, kaliumPreferences: KaliumPreferences) {
    private val dbName = FileNameUtil.userDBName(userId)
    private val driver: AndroidSqliteDriver
    private val database: UserDatabase

    init {
        val supportFactory = SupportFactory(DBUtil.getOrGenerateSecretKey(kaliumPreferences, DATABASE_SECRET_KEY).toByteArray())

        val onConnectCallback = object : AndroidSqliteDriver.Callback(UserDatabase.Schema) {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                db.execSQL("PRAGMA foreign_keys=ON;")
            }
        }

        driver = AndroidSqliteDriver(
            schema = UserDatabase.Schema,
            context = context,
            name = dbName,
            factory = supportFactory,
            callback = onConnectCallback
        )

        database = UserDatabase(
            driver,
            Client.Adapter(user_idAdapter = QualifiedIDAdapter()),
            Conversation.Adapter(
                qualified_idAdapter = QualifiedIDAdapter(),
                typeAdapter = EnumColumnAdapter(),
                mls_group_stateAdapter = EnumColumnAdapter(),
                protocolAdapter = EnumColumnAdapter(),
                muted_statusAdapter = EnumColumnAdapter()
            ),
            Member.Adapter(userAdapter = QualifiedIDAdapter(), conversationAdapter = QualifiedIDAdapter()),
            Message.Adapter(
                conversation_idAdapter = QualifiedIDAdapter(),
                sender_user_idAdapter = QualifiedIDAdapter(),
                statusAdapter = EnumColumnAdapter(),
                asset_image_widthAdapter = IntColumnAdapter,
                asset_image_heightAdapter = IntColumnAdapter,
                content_typeAdapter = ContentTypeAdapter(),
                visibilityAdapter = EnumColumnAdapter()
            ),
            User.Adapter(
                qualified_idAdapter = QualifiedIDAdapter(),
                accent_idAdapter = IntColumnAdapter,
                connection_statusAdapter = EnumColumnAdapter(),
            )
        )
    }

    actual val userDAO: UserDAO
        get() = UserDAOImpl(database.usersQueries)

    actual val conversationDAO: ConversationDAO
        get() = ConversationDAOImpl(database.conversationsQueries, database.usersQueries, database.membersQueries)

    actual val metadataDAO: MetadataDAO
        get() = MetadataDAOImpl(database.metadataQueries)

    actual val clientDAO: ClientDAO
        get() = ClientDAOImpl(database.clientsQueries)

    actual val messageDAO: MessageDAO
        get() = MessageDAOImpl(database.messagesQueries)

    actual val assetDAO: AssetDAO
        get() = AssetDAOImpl(database.assetsQueries)

    actual val teamDAO: TeamDAO
        get() = TeamDAOImpl(database.teamsQueries)

    actual fun nuke(): Boolean = DBUtil.deleteDB(driver, context, dbName)

    companion object {
        // FIXME(IMPORTANT): The same key is used to enc/dec all user DBs
        //                   Pain in the ass to migrate after release
        private const val DATABASE_SECRET_KEY = "user-db-secret"
    }
}
