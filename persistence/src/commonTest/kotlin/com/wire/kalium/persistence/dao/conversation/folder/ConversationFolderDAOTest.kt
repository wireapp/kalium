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
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
            conversationIdList = listOf(conversationEntity1.id))

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
            conversationIdList = listOf(conversationEntity1.id))

        db.conversationFolderDAO.updateConversationFolders(listOf(conversationFolderEntity))
        val result = db.conversationFolderDAO.getFavoriteConversationFolder()

        assertEquals(folderId, result.id)
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
