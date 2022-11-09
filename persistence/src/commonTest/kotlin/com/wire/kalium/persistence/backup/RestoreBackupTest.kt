package com.wire.kalium.persistence.backup

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.Member
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.utils.IgnoreJvm
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// There is some issue with restoring backup on JVM, investigation in progress
@IgnoreJvm
class RestoreBackupTest : BaseDatabaseTest() {

    private val backupUserIdEntity = UserIDEntity("backupValue", "backupDomain")

    private lateinit var userDatabaseBuilder: UserDatabaseBuilder
    private lateinit var backupDatabaseBuilder: UserDatabaseBuilder

    private lateinit var backupDatabaseDataGenerator: UserDatabaseDataGenerator
    private lateinit var userDatabaseDataGenerator: UserDatabaseDataGenerator

    @BeforeTest
    fun setUp() {
        deleteDatabase()
        userDatabaseBuilder = createDatabase()
        userDatabaseDataGenerator = UserDatabaseDataGenerator(
            userDatabaseBuilder = userDatabaseBuilder,
            databasePrefix = "user"

        )
        // TODO: using UserIDEntity is not correct, because the file will contain another user id
        // which we do want to preserve when restoring from the user database
        deleteDatabase(backupUserIdEntity)
        backupDatabaseBuilder = createDatabase(backupUserIdEntity)
        backupDatabaseDataGenerator = UserDatabaseDataGenerator(
            userDatabaseBuilder = backupDatabaseBuilder,
            databasePrefix = "backup"
        )
    }

    @Test
    fun givenBackupHasUniqueConversationAndUserHasUniqueConversations_whenRestoringBackup_TheBackupConversationAreInsertedInUserDatabase() =
        runTest {
            // given
            val backupConversationAmount = 100

            val conversationsToBackup = backupDatabaseDataGenerator.generateAndInsertConversations(
                conversationAmount = backupConversationAmount,
                messagePerConversation = 10,
                messageType = MessageType.Regular
            )

            val userConversationAmount = 50
            userDatabaseDataGenerator.generateAndInsertConversations(
                conversationAmount = userConversationAmount,
                messagePerConversation = 5,
                messageType = MessageType.Regular
            )
            // when
            userDatabaseBuilder.backupImporter.importFromFile(databasePath(backupUserIdEntity))

            // then
            val conversationsAfterBackup: List<ConversationEntity> = userDatabaseBuilder.conversationDAO.getAllConversations().first()

            assertTrue(conversationsAfterBackup.containsAll(conversationsToBackup))
            assertEquals(backupConversationAmount + userConversationAmount, conversationsAfterBackup.size)
        }

    @Test
    fun givenBackupHasSomeOverlappingConversationsWithTheUserAlongWithTheUniqueOnes_whenRestoringBackup_thenOnlyTheUniqueOnesAreRestored() =
        runTest {
            // given
            val uniqueUserConversationAmount = 25
            val userConversations = userDatabaseDataGenerator.generateAndInsertConversations(
                conversationAmount = uniqueUserConversationAmount,
                messagePerConversation = 5,
                messageType = MessageType.Regular
            )

            val uniqueBackupConversationAmount = 25
            val uniqueBackupConversations = backupDatabaseDataGenerator.generateAndInsertConversations(
                conversationAmount = uniqueBackupConversationAmount,
                messagePerConversation = 10,
                messageType = MessageType.Regular
            )

            backupDatabaseBuilder.conversationDAO.insertConversations(
                listOf(
                    userConversations[0],
                    userConversations[1],
                    userConversations[2]
                )
            )
            // when
            userDatabaseBuilder.backupImporter.importFromFile(databasePath(backupUserIdEntity))

            // then
            val conversationAfterRestore = userDatabaseBuilder.conversationDAO.getAllConversations().first()

            assertTrue(conversationAfterRestore.containsAll(uniqueBackupConversations))
            assertEquals(uniqueBackupConversationAmount + uniqueBackupConversationAmount, conversationAfterRestore.size)
        }

    @Test
    fun givenBackupHasAllUserConversationsAndBackupHasUniqueOnesAlongWithTheUser_whenRestoringBackup_thenOnlyTheUniqueOesAreRestored() =
        runTest {
            // given
            val uniqueUserConversationAmount = 25
            val userConversations = userDatabaseDataGenerator.generateAndInsertConversations(
                conversationAmount = uniqueUserConversationAmount,
                messagePerConversation = 5,
                messageType = MessageType.Regular
            )

            val uniqueBackupConversationAmount = 25
            val uniqueBackupConversations = backupDatabaseDataGenerator.generateAndInsertConversations(
                conversationAmount = uniqueBackupConversationAmount,
                messagePerConversation = 10,
                messageType = MessageType.Regular
            )

            backupDatabaseBuilder.conversationDAO.insertConversations(userConversations)
            // when
            userDatabaseBuilder.backupImporter.importFromFile(databasePath(backupUserIdEntity))

            // then
            val conversationAfterRestore = userDatabaseBuilder.conversationDAO.getAllConversations().first()

            assertTrue(conversationAfterRestore.containsAll(uniqueBackupConversations))
            assertEquals(uniqueBackupConversationAmount + uniqueBackupConversationAmount, conversationAfterRestore.size)
        }


    @Test
    fun givenBackupHasConversationsAndUserNone_whenRestoringBackup_thenThoseConversationAreRestored() = runTest {
        // given
        val backupConversations = backupDatabaseDataGenerator.generateAndInsertConversations(
            conversationAmount = 100,
            messagePerConversation = 25,
            messageType = MessageType.Regular
        )

        // when
        userDatabaseBuilder.backupImporter.importFromFile(databasePath(backupUserIdEntity))

        // then
        val conversationAfterRestore = userDatabaseBuilder.conversationDAO.getAllConversations().first()
        assertEquals(backupConversations, conversationAfterRestore)
    }

    @Test
    fun givenBackupHasOverLappingConversationWithUser_whenRestoringBackup_thenThoseConversationsAreNotInserted() = runTest {
        // given
        val overLappingConversations = insertOverlappingConversations(10)

        userDatabaseBuilder.conversationDAO.insertConversations(
            overLappingConversations
        )

        backupDatabaseBuilder.conversationDAO.insertConversations(
            overLappingConversations
        )

        // when
        userDatabaseBuilder.backupImporter.importFromFile(databasePath(backupUserIdEntity))

        // then
        assertEquals(10, userDatabaseBuilder.conversationDAO.getAllConversations().first().size)
    }

    @Test
    fun givenBackupHasNoConversationButUserDoes_whenRestoringBackup_thenThoseConversationAreRestored() = runTest {
        // given
        val userConversationAmount = 25
        val userConversations = backupDatabaseDataGenerator.generateAndInsertConversations(
            conversationAmount = userConversationAmount,
            messagePerConversation = 10,
            messageType = MessageType.Regular
        )

        backupDatabaseBuilder.conversationDAO.insertConversations(userConversations)

        // when
        userDatabaseBuilder.backupImporter.importFromFile(databasePath(backupUserIdEntity))

        // then
        val conversationAfterRestore = userDatabaseBuilder.conversationDAO.getAllConversations().first()
        assertEquals(userConversationAmount, conversationAfterRestore.size)
        assertTrue(conversationAfterRestore.containsAll(userConversations))
    }

    @Test
    fun givenBackupHasGroupConversationWithMembersAndUserNone_whenRestoringBackup_theThoseGroupMembersAndConversationAreRestored() =
        runTest {
            // given
            val backupConversations = backupDatabaseDataGenerator.generateAndInsertGroupConversations(
                conversationAmount = 100,
                membersPerGroup = 10
            )

            // when
            userDatabaseBuilder.backupImporter.importFromFile(databasePath(backupUserIdEntity))

            // then
            backupConversations.forEach { conversationEntity ->
                val backupMembers = backupDatabaseBuilder.conversationDAO.getAllMembers(conversationEntity.id).first()
                val restoredMembers = userDatabaseBuilder.conversationDAO.getAllMembers(conversationEntity.id).first()

                assertEquals(backupMembers, restoredMembers)
            }

            val restoredConversations = backupDatabaseBuilder.conversationDAO.getAllConversations().first()
            assertEquals(backupConversations, restoredConversations)
        }

    @Test
    fun givenBackupHasGroupConversationWithMembersAndUserAnotherGroupConversationWithSomeOfThoseMembers_whenRestoringBackup_thenTheOverlappingMembersAreNotRestored() =
        runTest {
            // given
            val overlappingBackupMembers = backupDatabaseDataGenerator.generateMembers(5)
            val backupConversationAmount = 5
            val backupConversations = backupDatabaseDataGenerator.generateAndInsertGroupConversations(
                conversationAmount = backupConversationAmount,
                membersGenerate = { overlappingBackupMembers }
            )

            val uniqueUserMembers = backupDatabaseDataGenerator.generateMembers(2)
            val userConversationAmount = 5
            userDatabaseDataGenerator.generateAndInsertGroupConversations(
                conversationAmount = userConversationAmount,
                membersGenerate = {
                    overlappingBackupMembers + uniqueUserMembers
                }
            )

            // when
            userDatabaseBuilder.backupImporter.importFromFile(databasePath(backupUserIdEntity))

            // then
            val restoredConversations = userDatabaseBuilder.conversationDAO.getAllConversations().first()
            assertEquals(userConversationAmount + backupConversationAmount, restoredConversations.size)
            assertTrue(restoredConversations.containsAll(backupConversations))

            val expectedMemberAmount = overlappingBackupMembers.size + uniqueUserMembers.size

            val restoredMembers = mutableListOf<Member>()

            restoredConversations.forEach { conversationEntity ->
                val members = userDatabaseBuilder.conversationDAO.getAllMembers(conversationEntity.id).first()

                members.forEach { member ->
                    if (!restoredMembers.contains(member)) {
                        restoredMembers.add(member)
                    }
                }
            }
            assertEquals(expectedMemberAmount, restoredMembers.size)
        }

    @Test
    fun givenBackupHasTeamsAndUserHasNoTeams_whenRestoringBackup_thenTeamsAreRestored() = runTest {
        // given
        val teams = backupDatabaseDataGenerator.generateTeams(25)
        teams.forEach { teamEntity ->
            backupDatabaseBuilder.teamDAO.insertTeam(teamEntity)
        }
        // when
        userDatabaseBuilder.backupImporter.importFromFile(databasePath(backupUserIdEntity))
        // then
        teams.forEach { teamEntity ->
            val restoredTeam = backupDatabaseBuilder.teamDAO.getTeamById(teamEntity.id).first()
            assertEquals(teamEntity, restoredTeam)
        }
    }

    @Test
    fun givenBackupHasNoTeamsANdUserHasTeams_whenRestoringBackup_thenTeamsArePresent() = runTest {
        // given
        val teams = userDatabaseDataGenerator.generateTeams(25)
        teams.forEach { teamEntity ->
            userDatabaseBuilder.teamDAO.insertTeam(teamEntity)
        }
        // when
        userDatabaseBuilder.backupImporter.importFromFile(databasePath(backupUserIdEntity))
        // then
        teams.forEach { teamEntity ->
            val restoredTeam = userDatabaseBuilder.teamDAO.getTeamById(teamEntity.id).first()
            assertEquals(teamEntity, restoredTeam)
        }
    }

    @Test
    fun givenBackupHasUniqueConversationWithCallsAndUser_whenRestoringBackup_thenBothCallsArePresents() = runTest {
        // given
        val backupConversationAmount = 3
        val conversationsWithCallToBackup = backupDatabaseDataGenerator.generateAndInsertConversationsWithCall(
            conversationAmount = backupConversationAmount
        )

        val userConversationAmount = 2
        userDatabaseDataGenerator.generateAndInsertConversationsWithCall(
            conversationAmount = userConversationAmount,
        )
        // when
        userDatabaseBuilder.backupImporter.importFromFile(databasePath(backupUserIdEntity))

        // then
        val conversationsAfterBackup: List<ConversationEntity> = userDatabaseBuilder.conversationDAO.getAllConversations().first()

        val conversations = conversationsWithCallToBackup.map { it.first }
        val calls = conversationsWithCallToBackup.map { it.second }

        assertTrue(conversationsAfterBackup.containsAll(conversations))
        assertEquals(backupConversationAmount + userConversationAmount, conversationsAfterBackup.size)

        val allCalls = userDatabaseBuilder.callDAO.observeCalls().first()
        assertTrue(allCalls.containsAll(calls))
    }

    @Test
    fun givenBackupHasConversationWithCallsButUserNot_whenRestoringBackup_thenAllCallsAreInserted() = runTest {
        // given
        val conversationsWithCallToBackup = backupDatabaseDataGenerator.generateAndInsertConversationsWithCall(
            conversationAmount = 10
        )

        // when
        userDatabaseBuilder.backupImporter.importFromFile(databasePath(backupUserIdEntity))

        // then
        val calls = conversationsWithCallToBackup.map { it.second }

        val allCalls = userDatabaseBuilder.callDAO.observeCalls().first()
        assertEquals(calls, allCalls)
    }

    @Test
    fun givenBackupHasNoConversationWithCallsButUserDoes_whenRestoringBackup_thenThoseCallsArePresent() = runTest {
        // given
        val conversationsWithCallToBackup = userDatabaseDataGenerator.generateAndInsertConversationsWithCall(
            conversationAmount = 10
        )

        // when
        userDatabaseBuilder.backupImporter.importFromFile(databasePath(backupUserIdEntity))

        // then
        val calls = conversationsWithCallToBackup.map { it.second }

        val allCalls = userDatabaseBuilder.callDAO.observeCalls().first()
        assertEquals(calls, allCalls)
    }

    @Test
    fun givenBackupHasUsersAndUserNot_whenRestoringBackup_thenThoseUsersAreRestored() = runTest {
        // given
        val userToBackup = backupDatabaseDataGenerator.generateAndInsertUsers(10)

        // when
        userDatabaseBuilder.backupImporter.importFromFile(databasePath(backupUserIdEntity))

        // then
        val restoredUsers = userDatabaseBuilder.userDAO.getAllUsers().first()
        assertEquals(userToBackup, restoredUsers)
    }

    @Test
    fun givenBackupHasNoUsersAndUserDoes_whenRestoringBackup_thenThoseUsersAreRestored() = runTest {
        // given
        val usersPresent = userDatabaseDataGenerator.generateAndInsertUsers(10)

        // when
        userDatabaseBuilder.backupImporter.importFromFile(databasePath(backupUserIdEntity))

        // then
        val restoredUsers = userDatabaseBuilder.userDAO.getAllUsers().first()
        assertEquals(usersPresent, restoredUsers)
    }

    @Test
    fun givenBackupHasUniqueUsersAndUserDoes_whenRestoringBackup_thenThoseUsersAreRestored() = runTest {
        // given
        val usersPresent = userDatabaseDataGenerator.generateAndInsertUsers(100)
        val userToBackup = backupDatabaseDataGenerator.generateAndInsertUsers(50)

        // when
        userDatabaseBuilder.backupImporter.importFromFile(databasePath(backupUserIdEntity))

        // then
        val restoredUsers = userDatabaseBuilder.userDAO.getAllUsers().first()
        assertTrue(restoredUsers.containsAll(userToBackup))
        assertEquals(usersPresent.size + userToBackup.size, restoredUsers.size)
    }

    @Test
    fun givenBackupHasOverlappingUsersWithUserAlongWithUniqueOnes_whenRestoringBackup_thenOnlyUniqueOnesAReRestored() = runTest {
        // given
        val uniqueUsersAmount = 10
        val uniqueBackupUsersAmount = 10

        val uniqueUsers = userDatabaseDataGenerator.generateAndInsertUsers(uniqueUsersAmount)
        val uniqueBackupUsers = backupDatabaseDataGenerator.generateAndInsertUsers(uniqueBackupUsersAmount)

        uniqueBackupUsers.forEach { userEntity ->
            backupDatabaseBuilder.userDAO.insertUser(userEntity)
        }

        // when
        userDatabaseBuilder.backupImporter.importFromFile(databasePath(backupUserIdEntity))

        // then
        val restoredUsers = userDatabaseBuilder.userDAO.getAllUsers().first()
        assertEquals(restoredUsers, uniqueUsers + uniqueBackupUsers)
        assertEquals(uniqueUsersAmount + uniqueBackupUsersAmount, restoredUsers.size)
    }

    @Test
    fun givenBackupHasAssetsButUserNot_whenRestoringBackup_thenBackupAssetsAreRestored() = runTest {
        // given
        val assetsToRestore = backupDatabaseDataGenerator.generateAndInsertAssets(10)

        // when
        userDatabaseBuilder.backupImporter.importFromFile(databasePath(backupUserIdEntity))

        // then
        assetsToRestore.forEach { restoredAssetEntity ->
            val asset = userDatabaseBuilder.assetDAO.getAssetByKey(restoredAssetEntity.key).first()
            assertTrue(asset != null)
            assertEquals(asset, restoredAssetEntity)
        }
    }

    @Test
    fun givenUserHasAssetButBackupNot_whenRestoringBackup_thenUserAssetAreRestored() = runTest {
        // given
        val assetsToRestore = userDatabaseDataGenerator.generateAndInsertAssets(10)

        // when
        userDatabaseBuilder.backupImporter.importFromFile(databasePath(backupUserIdEntity))

        // then
        assetsToRestore.forEach { restoredAssetEntity ->
            val asset = userDatabaseBuilder.assetDAO.getAssetByKey(restoredAssetEntity.key).first()

            assertTrue(asset != null)
            assertEquals(asset, restoredAssetEntity)
        }
    }

    @Test
    fun givenUserHasUniqueAssetsAlongWithBackup_whenRestoringBackup_thenThoseAssetsAreRestored() = runTest {
        // given
        val currentUserAssets = userDatabaseDataGenerator.generateAndInsertAssets(10)
        val backupAssets = backupDatabaseDataGenerator.generateAndInsertAssets(10)

        // when
        userDatabaseBuilder.backupImporter.importFromFile(databasePath(backupUserIdEntity))

        // then
        currentUserAssets.forEach { currentUserAsset ->
            val asset = userDatabaseBuilder.assetDAO.getAssetByKey(currentUserAsset.key).first()

            assertTrue(asset != null)
            assertEquals(asset, currentUserAsset)
        }
        backupAssets.forEach { backupAssetEntity ->
            val asset = userDatabaseBuilder.assetDAO.getAssetByKey(backupAssetEntity.key).first()

            assertTrue(asset != null)
            assertEquals(asset, backupAssetEntity)
        }
    }

    private suspend fun insertOverlappingConversations(amount: Int): List<ConversationEntity> {
        val conversationAdded = mutableListOf<ConversationEntity>()

        for (index in 1..amount) {
            val overLappingId = ConversationIDEntity("overlappingValue$index", "overlappingDomain$index")
            val overLappingName = "overLappingName$index"

            val conversationType = ConversationEntity.Type.values()[index % ConversationEntity.Type.values().size]

            val sanitizedConversationType =
                if (conversationType == ConversationEntity.Type.CONNECTION_PENDING)
                    ConversationEntity.Type.values()[(index + 1) % ConversationEntity.Type.values().size]
                else conversationType

            val overlappingConversation = ConversationEntity(
                id = overLappingId,
                name = overLappingName,
                type = sanitizedConversationType,
                teamId = null,
                protocolInfo = ConversationEntity.ProtocolInfo.Proteus,
                mutedStatus = ConversationEntity.MutedStatus.values()[index % ConversationEntity.MutedStatus.values().size],
                mutedTime = 0,
                removedBy = null,
                creatorId = "CreatorId$index",
                lastNotificationDate = UserDatabaseDataGenerator.DEFAULT_DATE_STRING,
                lastModifiedDate = UserDatabaseDataGenerator.DEFAULT_DATE_STRING,
                lastReadDate = UserDatabaseDataGenerator.DEFAULT_DATE_STRING,
                access = listOf(ConversationEntity.Access.values()[index % ConversationEntity.Access.values().size]),
                accessRole = listOf(ConversationEntity.AccessRole.values()[index % ConversationEntity.AccessRole.values().size]),
                isCreator = (index % 2 == 0)
            )

            conversationAdded.add(overlappingConversation)

            backupDatabaseBuilder.conversationDAO.insertConversation(overlappingConversation)

            userDatabaseBuilder.conversationDAO.insertConversation(overlappingConversation)
        }

        return conversationAdded
    }
}

