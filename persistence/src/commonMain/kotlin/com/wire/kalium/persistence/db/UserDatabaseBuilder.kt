/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.persistence.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.wire.kalium.persistence.HistoryClientQueries
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.backup.DatabaseExporter
import com.wire.kalium.persistence.backup.DatabaseExporterImpl
import com.wire.kalium.persistence.backup.DatabaseImporter
import com.wire.kalium.persistence.backup.DatabaseImporterImpl
import com.wire.kalium.persistence.backup.ObfuscatedCopyExporter
import com.wire.kalium.persistence.cache.FlowCache
import com.wire.kalium.persistence.dao.ConnectionDAO
import com.wire.kalium.persistence.dao.ConnectionDAOImpl
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.MetadataDAOImpl
import com.wire.kalium.persistence.dao.PrekeyDAO
import com.wire.kalium.persistence.dao.PrekeyDAOImpl
import com.wire.kalium.persistence.dao.SearchDAO
import com.wire.kalium.persistence.dao.SearchDAOImpl
import com.wire.kalium.persistence.dao.ServiceDAO
import com.wire.kalium.persistence.dao.ServiceDAOImpl
import com.wire.kalium.persistence.dao.TeamDAO
import com.wire.kalium.persistence.dao.TeamDAOImpl
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserDAOImpl
import com.wire.kalium.persistence.dao.UserDetailsEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.asset.AssetDAO
import com.wire.kalium.persistence.dao.asset.AssetDAOImpl
import com.wire.kalium.persistence.dao.call.CallDAO
import com.wire.kalium.persistence.dao.call.CallDAOImpl
import com.wire.kalium.persistence.dao.client.ClientDAO
import com.wire.kalium.persistence.dao.client.ClientDAOImpl
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationDAOImpl
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.conversation.ConversationMetaDataDAO
import com.wire.kalium.persistence.dao.conversation.ConversationMetaDataDAOImpl
import com.wire.kalium.persistence.dao.conversation.ConversationViewEntity
import com.wire.kalium.persistence.dao.conversation.folder.ConversationFolderDAO
import com.wire.kalium.persistence.dao.conversation.folder.ConversationFolderDAOImpl
import com.wire.kalium.persistence.dao.event.EventDAO
import com.wire.kalium.persistence.dao.event.EventDAOImpl
import com.wire.kalium.persistence.dao.member.MemberDAO
import com.wire.kalium.persistence.dao.member.MemberDAOImpl
import com.wire.kalium.persistence.dao.member.MemberEntity
import com.wire.kalium.persistence.dao.message.CompositeMessageDAO
import com.wire.kalium.persistence.dao.message.CompositeMessageDAOImpl
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageDAOImpl
import com.wire.kalium.persistence.dao.message.MessageMetadataDAO
import com.wire.kalium.persistence.dao.message.MessageMetadataDAOImpl
import com.wire.kalium.persistence.dao.message.attachment.MessageAttachmentsDao
import com.wire.kalium.persistence.dao.message.attachment.MessageAttachmentsDaoImpl
import com.wire.kalium.persistence.dao.message.draft.MessageDraftDAOImpl
import com.wire.kalium.persistence.dao.messageattachment.MessageAttachmentDraftDao
import com.wire.kalium.persistence.dao.messageattachment.MessageAttachmentDraftDaoImpl
import com.wire.kalium.persistence.dao.newclient.NewClientDAO
import com.wire.kalium.persistence.dao.newclient.NewClientDAOImpl
import com.wire.kalium.persistence.dao.reaction.ReactionDAO
import com.wire.kalium.persistence.dao.reaction.ReactionDAOImpl
import com.wire.kalium.persistence.dao.receipt.ReceiptDAO
import com.wire.kalium.persistence.dao.receipt.ReceiptDAOImpl
import com.wire.kalium.persistence.dao.unread.UserConfigDAO
import com.wire.kalium.persistence.dao.unread.UserConfigDAOImpl
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmInline

@JvmInline
value class UserDBSecret(val value: ByteArray)

/**
 * Dispatcher for database read operations.
 * Limited to [MAX_READ_PARALLELISM] (3) concurrent reads to prevent SQLCipher
 * connection pool exhaustion while maintaining good throughput.
 */
@JvmInline
value class ReadDispatcher(val value: CoroutineDispatcher)

/**
 * Dispatcher for database write operations.
 * Limited to [MAX_WRITE_PARALLELISM] (1) to serialize writes
 * and aligning with SQLite's single-writer model.
 */
@JvmInline
value class WriteDispatcher(val value: CoroutineDispatcher)

/**
 * Creates a [UserDatabaseBuilder] for the given [userId] and [passphrase]
 * @param platformDatabaseData Platform-specific data used to create the database
 * @param userId The user id of the database
 * @param passphrase The passphrase used to encrypt the database
 * @param dispatcher The dispatcher used to perform database operations
 * @param enableWAL Whether to enable WAL mode for the database https://www.sqlite.org/wal.html
 **/
expect fun userDatabaseBuilder(
    platformDatabaseData: PlatformDatabaseData,
    userId: UserIDEntity,
    passphrase: UserDBSecret?,
    dispatcher: CoroutineDispatcher,
    enableWAL: Boolean = true
): UserDatabaseBuilder

internal expect fun userDatabaseDriverByPath(
    platformDatabaseData: PlatformDatabaseData,
    path: String,
    passphrase: UserDBSecret?,
    enableWAL: Boolean
): SqlDriver

@Suppress("LongParameterList")
class UserDatabaseBuilder internal constructor(
    private val userId: UserIDEntity,
    internal val sqlDriver: SqlDriver,
    dispatcher: CoroutineDispatcher,
    private val platformDatabaseData: PlatformDatabaseData,
    private val isEncrypted: Boolean,
    private val queriesContext: CoroutineContext = KaliumDispatcherImpl.io,
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
        MessageLinkPreviewAdapter = TableMapper.messageLinkPreviewAdapter,
        MessageMentionAdapter = TableMapper.messageMentionAdapter,
        MessageMissedCallContentAdapter = TableMapper.messageMissedCallContentAdapter,
        MessageRestrictedAssetContentAdapter = TableMapper.messageRestrictedAssetContentAdapter,
        MessageTextContentAdapter = TableMapper.messageTextContentAdapter,
        MessageUnknownContentAdapter = TableMapper.messageUnknownContentAdapter,
        ReactionAdapter = TableMapper.reactionAdapter,
        ReceiptAdapter = TableMapper.receiptAdapter,
        SelfUserAdapter = TableMapper.selfUserAdapter,
        UserAdapter = TableMapper.userAdapter,
        MessageConversationReceiptModeChangedContentAdapter = TableMapper.messageConversationReceiptModeChangedContentAdapter,
        MessageNewConversationReceiptModeContentAdapter = TableMapper.messageNewConversationReceiptModeContentAdapter,
        UnreadEventAdapter = TableMapper.unreadEventAdapter,
        MessageConversationTimerChangedContentAdapter = TableMapper.messageConversationTimerChangedContentAdapter,
        ServiceAdapter = TableMapper.serviceAdapter,
        NewClientAdapter = TableMapper.newClientAdapter,
        MessageRecipientFailureAdapter = TableMapper.messageRecipientFailureAdapter,
        ButtonContentAdapter = TableMapper.buttonContentAdapter,
        MessageFederationTerminatedContentAdapter = TableMapper.messageFederationTerminatedContentAdapter,
        MessageConversationProtocolChangedContentAdapter = TableMapper.messageConversationProtocolChangedContentAdapter,
        MessageConversationLocationContentAdapter = TableMapper.messageConversationLocationContentAdapter,
        MessageLegalHoldContentAdapter = TableMapper.messageLegalHoldContentAdapter,
        MessageConversationProtocolChangedDuringACallContentAdapter =
            TableMapper.messageConversationProtocolChangedDuringACAllContentAdapter,
        ConversationLegalHoldStatusChangeNotifiedAdapter = TableMapper.conversationLegalHoldStatusChangeNotifiedAdapter,
        MessageAssetTransferStatusAdapter = TableMapper.messageAssetTransferStatusAdapter,
        MessageDraftAdapter = TableMapper.messageDraftsAdapter,
        LastMessageAdapter = TableMapper.lastMessageAdapter,
        LabeledConversationAdapter = TableMapper.labeledConversationAdapter,
        ConversationFolderAdapter = TableMapper.conversationFolderAdapter,
        MessageAttachmentDraftAdapter = TableMapper.messageAttachmentDraftAdapter,
        MessageAttachmentsAdapter = TableMapper.messageAttachmentsAdapter,
        HistoryClientAdapter = TableMapper.historyClientAdapter,
    )

    init {
        database.databasePropertiesQueries.insertSelfUserId(userId)
        database.databasePropertiesQueries.enableForeignKeyContraints()
    }

    private val readDispatcher: ReadDispatcher = ReadDispatcher(dispatcher.limitedParallelism(MAX_READ_PARALLELISM))
    private val writeDispatcher: WriteDispatcher = WriteDispatcher(dispatcher.limitedParallelism(MAX_WRITE_PARALLELISM))
    private val databaseScope = CoroutineScope(SupervisorJob() + dispatcher)

    private val userCache = FlowCache<UserIDEntity, UserDetailsEntity?>(databaseScope)
    val userDAO: UserDAO
        get() = UserDAOImpl(database.usersQueries, userCache, queriesContext)

    val messageMetaDataDAO: MessageMetadataDAO
        get() = MessageMetadataDAOImpl(database.messageMetadataQueries, readDispatcher)

    val userConfigDAO: UserConfigDAO
        get() = UserConfigDAOImpl(metadataDAO)

    val connectionDAO: ConnectionDAO
        get() = ConnectionDAOImpl(
            database.connectionsQueries,
            database.conversationsQueries,
            readDispatcher,
            writeDispatcher,
        )

    val eventDAO: EventDAO
        get() = EventDAOImpl(
            database.eventsQueries,
            readDispatcher,
            writeDispatcher,
        )

    private val conversationDetailsCache =
        FlowCache<ConversationIDEntity, ConversationViewEntity?>(databaseScope)

    private val conversationCache =
        FlowCache<ConversationIDEntity, ConversationEntity?>(databaseScope)
    val conversationDAO: ConversationDAO
        get() = ConversationDAOImpl(
            conversationDetailsCache,
            conversationCache,
            database.conversationsQueries,
            database.conversationDetailsQueries,
            database.conversationDetailsWithEventsQueries,
            database.membersQueries,
            database.unreadEventsQueries,
            readDispatcher,
            writeDispatcher,
        )

    val conversationFolderDAO: ConversationFolderDAO
        get() = ConversationFolderDAOImpl(
            database.conversationFoldersQueries,
            readDispatcher,
            writeDispatcher,
        )

    private val conversationMembersCache =
        FlowCache<ConversationIDEntity, List<MemberEntity>>(databaseScope)

    val memberDAO: MemberDAO
        get() = MemberDAOImpl(
            conversationMembersCache,
            database.membersQueries,
            database.usersQueries,
            database.conversationsQueries,
            readDispatcher,
            writeDispatcher,
        )

    private val metadataCache = FlowCache<String, String?>(databaseScope)
    val metadataDAO: MetadataDAO
        get() = MetadataDAOImpl(
            database.metadataQueries,
            metadataCache,
            databaseScope,
            writeDispatcher
        )

    val clientDAO: ClientDAO
        get() = ClientDAOImpl(database.clientsQueries, readDispatcher, writeDispatcher)

    val newClientDAO: NewClientDAO
        get() = NewClientDAOImpl(database.newClientQueries, readDispatcher, writeDispatcher)

    val databaseImporter: DatabaseImporter
        get() = DatabaseImporterImpl(
            this,
            database.importContentQueries,
            isEncrypted,
            platformDatabaseData
        )

    val databaseExporter: DatabaseExporter
        get() = DatabaseExporterImpl(userId, platformDatabaseData, this)

    val obfuscatedCopyExporter: ObfuscatedCopyExporter
        get() = ObfuscatedCopyExporter(userId, platformDatabaseData, this)

    val databaseOptimizer: DatabaseOptimizer
        get() = DatabaseOptimizer(this)

    val callDAO: CallDAO
        get() = CallDAOImpl(database.callsQueries, readDispatcher, writeDispatcher)

    val messageDAO: MessageDAO
        get() = MessageDAOImpl(
            database.messagesQueries,
            database.messageAttachmentsQueries,
            database.messageAssetViewQueries,
            database.notificationQueries,
            database.conversationsQueries,
            database.unreadEventsQueries,
            database.messagePreviewQueries,
            userId,
            database.reactionsQueries,
            database.usersQueries,
            readDispatcher,
            writeDispatcher,
            database.messageAssetTransferStatusQueries,
            database.buttonContentQueries
        )

    val messageDraftDAO = MessageDraftDAOImpl(
        database.messageDraftsQueries,
        database.messagesQueries,
        database.conversationsQueries,
        readDispatcher,
        writeDispatcher,
    )

    val assetDAO: AssetDAO
        get() = AssetDAOImpl(
            database.assetsQueries,
            readDispatcher,
            writeDispatcher
        )

    val teamDAO: TeamDAO
        get() = TeamDAOImpl(database.teamsQueries, readDispatcher, writeDispatcher)

    val reactionDAO: ReactionDAO
        get() = ReactionDAOImpl(database.reactionsQueries, readDispatcher, writeDispatcher)

    val receiptDAO: ReceiptDAO
        get() = ReceiptDAOImpl(database.receiptsQueries, TableMapper.receiptAdapter, readDispatcher, writeDispatcher)

    val prekeyDAO: PrekeyDAO
        get() = PrekeyDAOImpl(database.metadataQueries, readDispatcher, writeDispatcher)

    val compositeMessageDAO: CompositeMessageDAO
        get() = CompositeMessageDAOImpl(database.buttonContentQueries, writeDispatcher)

    val serviceDAO: ServiceDAO get() = ServiceDAOImpl(database.serviceQueries, readDispatcher, writeDispatcher)

    val searchDAO: SearchDAO get() = SearchDAOImpl(database.searchQueries, readDispatcher)
    val conversationMetaDataDAO: ConversationMetaDataDAO
        get() = ConversationMetaDataDAOImpl(
            database.conversationMetadataQueries,
            queriesContext
        )

    val messageAttachmentDraftDao: MessageAttachmentDraftDao
        get() = MessageAttachmentDraftDaoImpl(database.messageAttachmentDraftQueries, readDispatcher, writeDispatcher)

    val historyClientQueries: HistoryClientQueries
        get() = database.historyClientQueries

    val messageAttachments: MessageAttachmentsDao
        get() = MessageAttachmentsDaoImpl(database.messageAttachmentsQueries, readDispatcher, writeDispatcher)

    val debugExtension: DebugExtension
        get() = DebugExtension(
            sqlDriver = sqlDriver,
            metaDataDao = metadataDAO,
            isEncrypted = isEncrypted,
        )

    /**
     * @return the absolute path of the DB file or null if the DB file does not exist
     */
    fun dbFileLocation(): String? = getDatabaseAbsoluteFileLocation(platformDatabaseData, userId)

    /**
     * drops DB connection and delete the DB file
     */
    fun nuke(): Boolean {
        sqlDriver.close()
        databaseScope.cancel()
        return nuke(userId, platformDatabaseData)
    }

    private companion object {
        const val MAX_READ_PARALLELISM = 3
        const val MAX_WRITE_PARALLELISM = 1
    }
}

internal expect fun nuke(
    userId: UserIDEntity,
    platformDatabaseData: PlatformDatabaseData
): Boolean

internal expect fun getDatabaseAbsoluteFileLocation(
    platformDatabaseData: PlatformDatabaseData,
    userId: UserIDEntity
): String?

internal expect fun createEmptyDatabaseFile(
    platformDatabaseData: PlatformDatabaseData,
    userId: UserIDEntity,
): String?

@Suppress("TooGenericExceptionCaught")
fun SqlDriver.migrate(sqlSchema: SqlSchema<QueryResult.Value<Unit>>): Boolean {
    val oldVersion = this.executeQuery(null, "PRAGMA user_version;", {
        it.next()
        it.getLong(0).let { QueryResult.Value<Long?>(it) }
    }, 0).value ?: return false

    val newVersion = sqlSchema.version
    return try {
        if (oldVersion != newVersion) {
            sqlSchema.migrate(this, oldVersion, newVersion)
        }
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }
}

/**
 * @return true if the database have fk violations, false otherwise
 */
fun SqlDriver.checkFKViolations(): Boolean {
    var result = false
    executeQuery(null, "PRAGMA foreign_key_check;", {
        // foreign_key_check returns the rows with the fk violations
        // if the cursor has a next, it means there are violations
        // and the backup is corrupted
        if (it.next().value) {
            result = true
        }
        QueryResult.Unit
    }, 0, null)

    return result
}
