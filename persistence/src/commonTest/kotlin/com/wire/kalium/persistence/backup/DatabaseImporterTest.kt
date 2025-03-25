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

package com.wire.kalium.persistence.backup

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.call.CallEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.member.MemberEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.utils.IgnoreIOS
import com.wire.kalium.persistence.utils.IgnoreJvm
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// There is some issue with restoring backup on JVM, investigation in progress
@OptIn(ExperimentalCoroutinesApi::class)
@IgnoreJvm
@IgnoreIOS
class DatabaseImporterTest : BaseDatabaseTest() {


    private lateinit var userDatabaseBuilder: UserDatabaseBuilder
    private lateinit var backupDatabaseBuilder: UserDatabaseBuilder

    private lateinit var backupDatabaseDataGenerator: UserDatabaseDataGenerator
    private lateinit var userDatabaseDataGenerator: UserDatabaseDataGenerator

    private val selfUserId = UserIDEntity("selfValue", "selfDomain")
    private val backupUserIdEntity = UserIDEntity("backup-${selfUserId.value}", selfUserId.domain)

    private fun runTest(
        testBody: suspend TestScope.() -> Unit
    ): TestResult {
        return runTest(dispatcher, testBody = testBody)
    }

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        userDatabaseBuilder = createDatabase(selfUserId, passphrase = null, enableWAL = false)
        userDatabaseDataGenerator = UserDatabaseDataGenerator(
            userDatabaseBuilder = userDatabaseBuilder,
            databasePrefix = "user"

        )
        deleteDatabase(backupUserIdEntity)
        backupDatabaseBuilder = createDatabase(backupUserIdEntity, null, false)
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
            userDatabaseBuilder.databaseImporter.importFromFile(databasePath(backupUserIdEntity), false)

            // then
            val conversationsAfterBackup: List<ConversationEntity> =
                userDatabaseBuilder.conversationDAO.getAllConversations().first()

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
            userDatabaseBuilder.databaseImporter.importFromFile(databasePath(backupUserIdEntity), false)

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
            userDatabaseBuilder.databaseImporter.importFromFile(databasePath(backupUserIdEntity), false)

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
        userDatabaseBuilder.databaseImporter.importFromFile(databasePath(backupUserIdEntity), false)

        // then
        val conversationAfterRestore = userDatabaseBuilder.conversationDAO.getAllConversations().first()
        assertEquals(backupConversations, conversationAfterRestore)
    }

    @Test
    fun givenBackupHasOverLappingConversationWithUser_whenRestoringBackup_thenThoseConversationsAreNotInserted() = runTest {
        // given
        insertOverlappingConversations(10)

        // when
        userDatabaseBuilder.databaseImporter.importFromFile(databasePath(backupUserIdEntity), false)

        // then
        assertEquals(10, userDatabaseBuilder.conversationDAO.getAllConversations().first().size)
    }

    @Test
    fun givenBackupHasOverLappingConversationWithLastReadDate_whenRestoringBackup_thenTheRightLastReadDateIsRestored() = runTest {
        // given
        val readDateBackup = Instant.parse("2023-01-20T12:00:00.000Z")
        val readDateBackup2 = Instant.parse("2023-01-21T12:00:00.000Z")
        val backupConversation1 = backupDatabaseDataGenerator.generateAndInsertConversationWithLastReadDate(readDateBackup)
        val backupConversation2 = backupDatabaseDataGenerator.generateAndInsertConversationWithLastReadDate(readDateBackup2)

        val readDateCurrent = Instant.parse("2023-01-23T12:00:00.000Z")
        val currentConversation = backupConversation1.copy(lastReadDate = readDateCurrent)
        userDatabaseBuilder.conversationDAO.insertConversation(currentConversation)

        // when
        userDatabaseBuilder.databaseImporter.importFromFile(databasePath(backupUserIdEntity), false)

        // then
        assertEquals(
            userDatabaseBuilder.conversationDAO.getConversationDetailsById(backupConversation1.id)?.lastReadDate,
            readDateCurrent
        )
        assertEquals(
            userDatabaseBuilder.conversationDAO.getConversationDetailsById(backupConversation2.id)?.lastReadDate,
            readDateBackup2
        )
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
        userDatabaseBuilder.databaseImporter.importFromFile(databasePath(backupUserIdEntity), false)

        // then
        val conversationAfterRestore = userDatabaseBuilder.conversationDAO.getAllConversations().first()
        assertEquals(userConversationAmount, conversationAfterRestore.size)
        assertTrue(conversationAfterRestore.containsAll(userConversations))
    }

    @Test
    fun givenBackupHasGroupConversationWithMembersAndUserNone_whenRestoringBackup_thenThoseConversationAreRestoredButMembersNot() =
        runTest {
            // given
            val membersPerGroup = 10
            val backupConversations = backupDatabaseDataGenerator.generateAndInsertGroupConversations(
                conversationAmount = 100,
                membersPerGroup = membersPerGroup
            )

            // when
            userDatabaseBuilder.databaseImporter.importFromFile(databasePath(backupUserIdEntity), false)

            // then
            backupConversations.forEach { conversationEntity ->
                val restoredMembers = userDatabaseBuilder.memberDAO.observeConversationMembers(conversationEntity.id).first()
                assertEquals(emptyList(), restoredMembers) // We don't restore members from backups. They are synchronized from the backend
            }

            val restoredConversations = backupDatabaseBuilder.conversationDAO.getAllConversations().first()
            assertEquals(backupConversations, restoredConversations)
        }

    @Test
    fun givenBackupHasConversationWithMembersAndUseWithSomeOfThoseMembers_whenRestoringBackup_thenTheOverlappingMembersAreNotRestored() =
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
            userDatabaseBuilder.databaseImporter.importFromFile(databasePath(backupUserIdEntity), false)

            // then
            val restoredConversations = userDatabaseBuilder.conversationDAO.getAllConversations().first()
            assertEquals(userConversationAmount + backupConversationAmount, restoredConversations.size)
            assertTrue(restoredConversations.containsAll(backupConversations))

            val expectedMemberAmount = overlappingBackupMembers.size + uniqueUserMembers.size

            val restoredMembers = mutableListOf<MemberEntity>()

            restoredConversations.forEach { conversationEntity ->
                val members = userDatabaseBuilder.memberDAO.observeConversationMembers(conversationEntity.id).first()

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
        userDatabaseBuilder.databaseImporter.importFromFile(databasePath(backupUserIdEntity), false)
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
        userDatabaseBuilder.databaseImporter.importFromFile(databasePath(backupUserIdEntity), false)
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
        userDatabaseBuilder.databaseImporter.importFromFile(databasePath(backupUserIdEntity), false)

        // then
        val conversationsAfterBackup: List<ConversationEntity> = userDatabaseBuilder.conversationDAO.getAllConversations().first()

        val calls = conversationsWithCallToBackup.map {
            val call = it.second
            call.copy(
                status = if (call.status != CallEntity.Status.CLOSED && call.status != CallEntity.Status.MISSED) {
                    CallEntity.Status.CLOSED
                } else {
                    call.status
                }
            )
        }

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
        userDatabaseBuilder.databaseImporter.importFromFile(databasePath(backupUserIdEntity), false)

        // then
        val calls = conversationsWithCallToBackup.map {
            val call = it.second
            call.copy(
                status = if (call.status != CallEntity.Status.CLOSED && call.status != CallEntity.Status.MISSED) {
                    CallEntity.Status.CLOSED
                } else {
                    call.status
                }
            )
        }

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
        userDatabaseBuilder.databaseImporter.importFromFile(databasePath(backupUserIdEntity), false)

        // then
        val calls = conversationsWithCallToBackup.map { it.second }

        val allCalls = userDatabaseBuilder.callDAO.observeCalls().first()
        assertEquals(calls, allCalls)
    }

    @Test
    fun givenBackupHasUniqueConversationWithCallsButFromOtherClient_whenRestoringBackup_thenNoCallsAreInserted() = runTest {
        // given
        val backupConversationAmount = 3
        val backupConversationsWithCalls = backupDatabaseDataGenerator.generateAndInsertConversationsWithCall(
            conversationAmount = backupConversationAmount
        )

        val userConversationAmount = 2
        val userConversationsWithCalls = userDatabaseDataGenerator.generateAndInsertConversationsWithCall(
            conversationAmount = userConversationAmount
        )
        // when
        userDatabaseBuilder.databaseImporter.importFromFile(databasePath(backupUserIdEntity), true)

        // then
        val conversationsAfterBackup: List<ConversationEntity> = userDatabaseBuilder.conversationDAO.getAllConversations().first()

        val backupCalls = backupConversationsWithCalls.map { it.second }
        val userCalls = userConversationsWithCalls.map { it.second }

        assertEquals(backupConversationAmount + userConversationAmount, conversationsAfterBackup.size)

        val allCalls = userDatabaseBuilder.callDAO.observeCalls().first()
        assertEquals(allCalls, userCalls)
        assertEquals(allCalls.minus(backupCalls.toSet()), allCalls)
    }

    @Test
    fun givenBackupHasUsersAndUserNot_whenRestoringBackup_thenThoseUsersAreRestored() = runTest {
        // given
        val userToBackup = backupDatabaseDataGenerator.generateAndInsertUsers(10)

        // when
        userDatabaseBuilder.databaseImporter.importFromFile(databasePath(backupUserIdEntity), false)

        // then
        val restoredUsers = userDatabaseBuilder.userDAO.getAllUsersDetails().first()
        assertEquals(userToBackup, restoredUsers)
    }

    @Test
    fun givenBackupHasNoUsersAndUserDoes_whenRestoringBackup_thenThoseUsersAreRestored() = runTest {
        // given
        val usersPresent = userDatabaseDataGenerator.generateAndInsertUsers(10)

        // when
        userDatabaseBuilder.databaseImporter.importFromFile(databasePath(backupUserIdEntity), false)

        // then
        val restoredUsers = userDatabaseBuilder.userDAO.getAllUsersDetails().first()
        assertEquals(usersPresent, restoredUsers)
    }

    @Test
    fun givenBackupHasUniqueUsersAndUserDoes_whenRestoringBackup_thenThoseUsersAreRestored() = runTest {
        // given
        val usersPresent = userDatabaseDataGenerator.generateAndInsertUsers(100)
        val userToBackup = backupDatabaseDataGenerator.generateAndInsertUsers(50)

        // when
        userDatabaseBuilder.databaseImporter.importFromFile(databasePath(backupUserIdEntity), false)

        // then
        val restoredUsers = userDatabaseBuilder.userDAO.getAllUsersDetails().first()
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
            backupDatabaseBuilder.userDAO.upsertUser(userEntity.toSimpleEntity())
        }

        // when
        userDatabaseBuilder.databaseImporter.importFromFile(databasePath(backupUserIdEntity), false)

        // then
        val restoredUsers = userDatabaseBuilder.userDAO.getAllUsersDetails().first()
        assertEquals(restoredUsers, uniqueBackupUsers + uniqueUsers)
        assertEquals(uniqueUsersAmount + uniqueBackupUsersAmount, restoredUsers.size)
    }

    @Test
    fun givenBackupHasAssets_whenRestoringBackup_thenThoseAssetsAreNotRestored() = runTest {
        // given
        val assetsToRestore = backupDatabaseDataGenerator.generateAndInsertAssets(10)

        // when
        userDatabaseBuilder.databaseImporter.importFromFile(databasePath(backupUserIdEntity), false)

        // then
        assetsToRestore.forEach { assetEntity ->
            val asset = userDatabaseBuilder.assetDAO.getAssetByKey(assetEntity.key).first()
            assertNull(asset)
        }
    }

    @Test
    fun givenBackupHasAssetsAndUser_whenRestoringBackup_thenBackupAssetsAreNotRestoredButUsersAssetArePreserved() = runTest {
        // given
        val assetsToRestore = backupDatabaseDataGenerator.generateAndInsertAssets(10)
        val userAssets = userDatabaseDataGenerator.generateAndInsertAssets(10)

        // when
        userDatabaseBuilder.databaseImporter.importFromFile(databasePath(backupUserIdEntity), false)

        // then
        assetsToRestore.forEach { assetEntity ->
            val asset = userDatabaseBuilder.assetDAO.getAssetByKey(assetEntity.key).first()
            assertNull(asset)
        }
        userAssets.forEach { assetEntity ->
            val asset = userDatabaseBuilder.assetDAO.getAssetByKey(assetEntity.key).first()
            assertEquals(assetEntity, asset)
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
                lastNotificationDate = UserDatabaseDataGenerator.DEFAULT_DATE,
                lastModifiedDate = UserDatabaseDataGenerator.DEFAULT_DATE,
                lastReadDate = UserDatabaseDataGenerator.DEFAULT_DATE,
                access = listOf(ConversationEntity.Access.values()[index % ConversationEntity.Access.values().size]),
                accessRole = listOf(ConversationEntity.AccessRole.values()[index % ConversationEntity.AccessRole.values().size]),
                receiptMode = ConversationEntity.ReceiptMode.DISABLED,
                messageTimer = null,
                userMessageTimer = null,
                archived = false,
                archivedInstant = null,
                mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
                proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
                legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED,
                isChannel = false,
            )

            conversationAdded.add(overlappingConversation)

            backupDatabaseBuilder.conversationDAO.insertConversation(overlappingConversation)

            userDatabaseBuilder.conversationDAO.insertConversation(overlappingConversation)
        }

        return conversationAdded
    }
}
