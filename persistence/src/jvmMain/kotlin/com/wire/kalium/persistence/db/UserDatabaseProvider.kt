package com.wire.kalium.persistence.db

import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wire.kalium.persistence.Client
import com.wire.kalium.persistence.Conversation
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
import com.wire.kalium.persistence.dao.QualifiedIDListAdapter
import com.wire.kalium.persistence.dao.TeamDAO
import com.wire.kalium.persistence.dao.TeamDAOImpl
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserDAOImpl
import com.wire.kalium.persistence.dao.asset.AssetDAO
import com.wire.kalium.persistence.dao.asset.AssetDAOImpl
import com.wire.kalium.persistence.dao.client.ClientDAO
import com.wire.kalium.persistence.dao.client.ClientDAOImpl
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageDAOImpl
import java.io.File
import java.util.Properties

actual class UserDatabaseProvider(private val storePath: File) {

    private val database: UserDatabase

    init {
        val databasePath = storePath.resolve(DATABASE_NAME)
        val databaseExists = databasePath.exists()

        // Make sure all intermediate directories exist
        storePath.mkdirs()

        val driver: SqlDriver = JdbcSqliteDriver(
            "jdbc:sqlite:${databasePath.absolutePath}",
            Properties(1).apply { put("foreign_keys", "true") })

        if (!databaseExists) {
            UserDatabase.Schema.create(driver)
        }

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
            Member.Adapter(
                userAdapter = QualifiedIDAdapter(),
                conversationAdapter = QualifiedIDAdapter()),
            Message.Adapter(
                conversation_idAdapter = QualifiedIDAdapter(),
                sender_user_idAdapter = QualifiedIDAdapter(),
                statusAdapter = EnumColumnAdapter(),
                asset_download_statusAdapter = EnumColumnAdapter(),
                asset_image_widthAdapter = IntColumnAdapter,
                asset_image_heightAdapter = IntColumnAdapter,
                content_typeAdapter = ContentTypeAdapter(),
                visibilityAdapter = EnumColumnAdapter(),
                member_listAdapter = QualifiedIDListAdapter()
            ),
            User.Adapter(
                qualified_idAdapter = QualifiedIDAdapter(),
                accent_idAdapter = IntColumnAdapter,
                connection_statusAdapter = EnumColumnAdapter()
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

    actual fun nuke(): Boolean {
        return storePath.resolve(DATABASE_NAME).delete()
    }

    private companion object {
        // FIXME: user id/domain as the db name
        const val DATABASE_NAME = "main.db"
    }
}
