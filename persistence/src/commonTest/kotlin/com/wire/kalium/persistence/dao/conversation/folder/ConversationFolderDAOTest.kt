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
package com.wire.kalium.persistence.dao.conversation.folder

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.member.MemberEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConversationFolderDAOTest : BaseDatabaseTest() {

    private val conversationEntity1 = newConversationEntity("Test1").copy(type = ConversationEntity.Type.GROUP)
    private val userEntity1 = newUserEntity("userEntity1")
    val member1 = MemberEntity(userEntity1.id, MemberEntity.Role.Admin)

    lateinit var db: UserDatabaseBuilder
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        db = createDatabase(selfUserId, encryptedDBSecret, true)
    }

    @Test
    fun givenFolderWithConversationId_WhenObservingThenConversationShouldBeReturned() = runTest {
        db.conversationDAO.insertConversation(conversationEntity1)
        db.userDAO.upsertUser(userEntity1)
        db.memberDAO.insertMember(member1, conversationEntity1.id)

        val folderId = "folderId1"

        val conversationFolderEntity = folderWithConversationsEntity(
            id = folderId,
            name = "folderName",
            type = ConversationFolderTypeEntity.USER,
            conversationIdList = listOf(conversationEntity1.id)
        )

        db.conversationFolderDAO.updateConversationFolders(listOf(conversationFolderEntity))
        val result = db.conversationFolderDAO.observeConversationListFromFolder(folderId).first().first()

        assertEquals(conversationEntity1.id, result.conversationViewEntity.id)
    }

    @Test
    fun givenFavoriteFolderWithConversationId_WhenObservingThenFavoriteConversationShouldBeReturned() = runTest {
        db.conversationDAO.insertConversation(conversationEntity1)
        db.userDAO.upsertUser(userEntity1)
        db.memberDAO.insertMember(member1, conversationEntity1.id)

        val folderId = "folderId1"

        val conversationFolderEntity = folderWithConversationsEntity(
            id = folderId,
            name = "",
            type = ConversationFolderTypeEntity.FAVORITE,
            conversationIdList = listOf(conversationEntity1.id)
        )

        db.conversationFolderDAO.updateConversationFolders(listOf(conversationFolderEntity))
        val result = db.conversationFolderDAO.getFavoriteConversationFolder()

        assertEquals(folderId, result?.id)
    }

    @Test
    fun givenMultipleFolders_whenRetrievingFolders_shouldReturnCorrectData() = runTest {
        db.conversationDAO.insertConversation(conversationEntity1)
        db.userDAO.upsertUser(userEntity1)
        db.memberDAO.insertMember(member1, conversationEntity1.id)

        val folder1 = folderWithConversationsEntity(
            id = "folderId1",
            name = "Folder 1",
            type = ConversationFolderTypeEntity.USER,
            conversationIdList = listOf(conversationEntity1.id)
        )

        val folder2 = folderWithConversationsEntity(
            id = "folderId2",
            name = "Folder 2",
            type = ConversationFolderTypeEntity.USER,
            conversationIdList = listOf()
        )

        db.conversationFolderDAO.updateConversationFolders(listOf(folder1, folder2))
        val result = db.conversationFolderDAO.getFoldersWithConversations()

        assertEquals(2, result.size)
        assertTrue(result.any { it.id == "folderId1" && it.name == "Folder 1" })
        assertTrue(result.any { it.id == "folderId2" && it.name == "Folder 2" })
    }

    @Test
    fun givenFolderWithConversation_whenRemovingConversation_thenFolderShouldBeEmpty() = runTest {
        db.conversationDAO.insertConversation(conversationEntity1)
        db.userDAO.upsertUser(userEntity1)
        db.memberDAO.insertMember(member1, conversationEntity1.id)

        val folderId = "folderId1"
        val folder = folderWithConversationsEntity(
            id = folderId,
            name = "Test Folder",
            type = ConversationFolderTypeEntity.USER,
            conversationIdList = listOf(conversationEntity1.id)
        )

        db.conversationFolderDAO.updateConversationFolders(listOf(folder))
        db.conversationFolderDAO.removeConversationFromFolder(conversationEntity1.id, folderId)

        val result = db.conversationFolderDAO.observeConversationListFromFolder(folderId).first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun givenFolderWithConversations_whenDeletingFolder_thenFolderShouldBeRemoved() = runTest {
        db.conversationDAO.insertConversation(conversationEntity1)
        db.userDAO.upsertUser(userEntity1)
        db.memberDAO.insertMember(member1, conversationEntity1.id)

        val folder = folderWithConversationsEntity(
            id = "folderId1",
            name = "Folder 1",
            type = ConversationFolderTypeEntity.USER,
            conversationIdList = listOf(conversationEntity1.id)
        )

        db.conversationFolderDAO.updateConversationFolders(listOf(folder))
        db.conversationFolderDAO.updateConversationFolders(listOf()) // Clear folders

        val result = db.conversationFolderDAO.getFoldersWithConversations()

        assertTrue(result.isEmpty())
    }

    @Test
    fun givenEmptyFolder_whenAddingToDatabase_thenShouldBeRetrievable() = runTest {
        val folder = folderWithConversationsEntity(
            id = "folderId1",
            name = "Empty Folder",
            type = ConversationFolderTypeEntity.USER,
            conversationIdList = listOf()
        )

        db.conversationFolderDAO.updateConversationFolders(listOf(folder))

        val result = db.conversationFolderDAO.getFoldersWithConversations()

        assertEquals(1, result.size)
        assertEquals("folderId1", result.first().id)
        assertTrue(result.first().conversationIdList.isEmpty())
    }

    @Test
    fun givenConversationAddedToUserAndFavoriteFolders_whenRetrievingFolders_thenShouldBeInBothFolders() = runTest {
        db.conversationDAO.insertConversation(conversationEntity1)
        db.userDAO.upsertUser(userEntity1)
        db.memberDAO.insertMember(member1, conversationEntity1.id)

        val userFolder = folderWithConversationsEntity(
            id = "userFolderId",
            name = "User Folder",
            type = ConversationFolderTypeEntity.USER,
            conversationIdList = listOf(conversationEntity1.id)
        )

        val favoriteFolder = folderWithConversationsEntity(
            id = "favoriteFolderId",
            name = "Favorites",
            type = ConversationFolderTypeEntity.FAVORITE,
            conversationIdList = listOf(conversationEntity1.id)
        )

        db.conversationFolderDAO.updateConversationFolders(listOf(userFolder, favoriteFolder))

        val userFolderResult = db.conversationFolderDAO.observeConversationListFromFolder("userFolderId").first()
        assertEquals(1, userFolderResult.size)
        assertEquals(conversationEntity1.id, userFolderResult.first().conversationViewEntity.id)

        val favoriteFolderResult = db.conversationFolderDAO.observeConversationListFromFolder("favoriteFolderId").first()
        assertEquals(1, favoriteFolderResult.size)
        assertEquals(conversationEntity1.id, favoriteFolderResult.first().conversationViewEntity.id)
    }

    @Test
    fun givenExistingFolder_whenRemovingFolder_thenFolderShouldBeDeleted() = runTest {
        val folderId = "folderId1"

        val folder = folderWithConversationsEntity(
            id = folderId,
            name = "Test Folder",
            type = ConversationFolderTypeEntity.USER,
            conversationIdList = listOf(conversationEntity1.id)
        )

        db.conversationFolderDAO.updateConversationFolders(listOf(folder))
        assertEquals(1, db.conversationFolderDAO.getFoldersWithConversations().size)

        db.conversationFolderDAO.removeFolder(folderId)

        val result = db.conversationFolderDAO.getFoldersWithConversations()
        assertTrue(result.none { it.id == folderId })
    }

    @Test
    fun givenNonExistentFolder_whenRemovingFolder_thenNoErrorShouldBeThrown() = runTest {
        val nonExistentFolderId = "nonExistentFolderId"

        db.conversationFolderDAO.removeFolder(nonExistentFolderId)

        val result = db.conversationFolderDAO.getFoldersWithConversations()
        assertTrue(result.isEmpty())
    }

    @Test
    fun givenFolderWithConversations_whenRemovingFolder_thenFolderAndConversationsShouldBeDeleted() = runTest {
        val folderId = "folderId1"

        val folder = folderWithConversationsEntity(
            id = folderId,
            name = "Test Folder",
            type = ConversationFolderTypeEntity.USER,
            conversationIdList = listOf(conversationEntity1.id)
        )

        db.conversationFolderDAO.updateConversationFolders(listOf(folder))
        db.conversationFolderDAO.removeFolder(folderId)

        val folderResult = db.conversationFolderDAO.getFoldersWithConversations()
        assertTrue(folderResult.none { it.id == folderId })

        val conversationResult = db.conversationFolderDAO.observeConversationListFromFolder(folderId).firstOrNull()
        assertTrue(conversationResult.isNullOrEmpty())
    }

    @Test
    fun givenMultipleFolders_whenRemovingOneFolder_thenOthersShouldRemain() = runTest {
        val folder1 = folderWithConversationsEntity(
            id = "folderId1",
            name = "Folder 1",
            type = ConversationFolderTypeEntity.USER,
            conversationIdList = listOf(conversationEntity1.id)
        )

        val folder2 = folderWithConversationsEntity(
            id = "folderId2",
            name = "Folder 2",
            type = ConversationFolderTypeEntity.USER,
            conversationIdList = listOf()
        )

        db.conversationFolderDAO.updateConversationFolders(listOf(folder1, folder2))
        db.conversationFolderDAO.removeFolder("folderId1")

        val result = db.conversationFolderDAO.getFoldersWithConversations()
        assertEquals(1, result.size)
        assertTrue(result.any { it.id == "folderId2" })
    }

    @Test
    fun givenFolder_whenAddingFolder_thenFolderShouldBeAddedToDatabase() = runTest {
        val folderId = "folder1"
        val folderName = "Test Folder"
        val folderType = ConversationFolderTypeEntity.USER
        val folder = ConversationFolderEntity(id = folderId, name = folderName, type = folderType)

        db.conversationFolderDAO.addFolder(folder)

        val result = db.conversationFolderDAO.getFoldersWithConversations()

        assertEquals(1, result.size)
        val addedFolder = result.first()
        assertEquals(folderId, addedFolder.id)
        assertEquals(folderName, addedFolder.name)
        assertEquals(folderType, addedFolder.type)
    }

    companion object {
        fun folderWithConversationsEntity(
            id: String = "folderId",
            type: ConversationFolderTypeEntity = ConversationFolderTypeEntity.FAVORITE,
            name: String = "",
            conversationIdList: List<QualifiedIDEntity> = listOf(QualifiedIDEntity("conversationId", "domain"))
        ) = FolderWithConversationsEntity(
            id = id,
            type = type,
            name = name,
            conversationIdList = conversationIdList
        )
    }
}
