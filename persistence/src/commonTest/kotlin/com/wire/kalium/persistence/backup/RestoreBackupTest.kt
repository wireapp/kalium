package com.wire.kalium.persistence.backup

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.DefaultDatabaseTestValues
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class RestoreBackupTest : BaseDatabaseTest() {

    private lateinit var currentDatabase: UserDatabaseBuilder
    private val backup = UserIDEntity("backupValue", "backupDomain")
    private lateinit var backupDatabase: UserDatabaseBuilder

    @BeforeTest
    fun setUp() {
        currentDatabase = createDatabase()
        backupDatabase = createDatabase(backup)
    }

    @Test
    fun test() = runTest {
        currentDatabase.userDAO.insertUser(
            UserEntity(
                id = UserIDEntity("testUserValue", "testUserDomain"),
                availabilityStatus = UserAvailabilityStatusEntity.AVAILABLE,
                userType = UserTypeEntity.OWNER,
                deleted = false,
                name = null,
                handle = null,
                email = null,
                phone = null,
                accentId = 0,
                team = null,
                connectionStatus = ConnectionEntity.State.ACCEPTED,
                previewAssetId = null,
                completeAssetId = null,
                botService = null
            )
        )

        currentDatabase.conversationDAO.insertConversation(
            ConversationEntity(
                id = QualifiedIDEntity(
                    "testValue",
                    "testDomain"
                ),
                name = "testName",
                type = ConversationEntity.Type.ONE_ON_ONE,
                teamId = null,
                protocolInfo = ConversationEntity.ProtocolInfo.Proteus,
                mutedStatus = ConversationEntity.MutedStatus.ALL_ALLOWED,
                mutedTime = 0,
                removedBy = null,
                creatorId = "testUser",
                lastNotificationDate = null,
                lastModifiedDate = "2000-01-01T12:00:00.000Z",
                lastReadDate = "2000-01-01T12:00:00.000Z",
                access = listOf(),
                accessRole = listOf(),
                isCreator = false
            )
        )
        currentDatabase.messageDAO.insertMessage(
            newRegularMessageEntity(
                id = "1",
                conversationId = QualifiedIDEntity("testValue", "testDomain"),
                senderUserId = QualifiedIDEntity("testUserValue", "testUserDomain")
            )
        )

        backupDatabase.userDAO.insertUser(
            UserEntity(
                id = UserIDEntity("test2UserValue", "test2UserDomain"),
                availabilityStatus = UserAvailabilityStatusEntity.AVAILABLE,
                userType = UserTypeEntity.OWNER,
                deleted = false,
                name = null,
                handle = null,
                email = null,
                phone = null,
                accentId = 0,
                team = null,
                connectionStatus = ConnectionEntity.State.ACCEPTED,
                previewAssetId = null,
                completeAssetId = null,
                botService = null
            )
        )

        backupDatabase.conversationDAO.insertConversation(
            ConversationEntity(
                id = QualifiedIDEntity(
                    "test2Value",
                    "test2Domain"
                ),
                name = "testName",
                type = ConversationEntity.Type.ONE_ON_ONE,
                teamId = null,
                protocolInfo = ConversationEntity.ProtocolInfo.Proteus,
                mutedStatus = ConversationEntity.MutedStatus.ALL_ALLOWED,
                mutedTime = 0,
                removedBy = null,
                creatorId = "testUser",
                lastNotificationDate = null,
                lastModifiedDate = "2000-01-01T12:00:00.000Z",
                lastReadDate = "2000-01-01T12:00:00.000Z",
                access = listOf(),
                accessRole = listOf(),
                isCreator = false
            )
        )
        backupDatabase.messageDAO.insertMessage(
            newRegularMessageEntity(
                id = "1",
                conversationId = QualifiedIDEntity("test2Value", "test2Domain"),
                senderUserId = QualifiedIDEntity("test2UserValue", "test2UserDomain")
            )
        )

        println("database path of back up :${databasePath(backup)}/main.db")
        println("database path of user database:${databasePath()}/main.db")

        currentDatabase.backupImporter.importFromFile("/Users/Mateusz/AndroidStudioProjects/kalium/persistence/src/commonTest/kotlin/com/wire/kalium/persistence/main.db")
//         currentDatabase.backupImporter.importFromFile("/var/folders/b1/2qw84dsd7bb48qw_3yz9vll80000gp/T/test-storage3850219063162656586/test-backupDomain-backupValue.db/main.db")
    }

}
