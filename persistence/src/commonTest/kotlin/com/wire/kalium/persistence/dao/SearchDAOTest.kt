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
package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.member.MemberDAO
import com.wire.kalium.persistence.dao.member.MemberEntity
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchDAOTest : BaseDatabaseTest() {

    private lateinit var searchDAO: SearchDAO
    private lateinit var userDAO: UserDAO
    private lateinit var conversationDAO: ConversationDAO
    private lateinit var memberDAO: MemberDAO
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        val db = createDatabase(selfUserId, encryptedDBSecret, true)
        searchDAO = db.searchDAO
        userDAO = db.userDAO
        conversationDAO = db.conversationDAO
        memberDAO = db.memberDAO
    }

    @Test
    fun givenConnectedUser_whenGettingKnowUsers_thenReturnOnlyConnectedUsers() = runTest {

        val connectedUser1 = newUserEntity(id = "1").copy(connectionStatus = ConnectionEntity.State.ACCEPTED)
        val connectedUser2 = newUserEntity(id = "2").copy(connectionStatus = ConnectionEntity.State.ACCEPTED)
        val pendingUser = newUserEntity(id = "pendingUser").copy(connectionStatus = ConnectionEntity.State.PENDING)
        val blockedUser = newUserEntity(id = "blockedUser").copy(connectionStatus = ConnectionEntity.State.BLOCKED)
        val notConnectedUser = newUserEntity(id = "notConnectedUser").copy(connectionStatus = ConnectionEntity.State.NOT_CONNECTED)
        val ignoredUser = newUserEntity(id = "ignoredUser").copy(connectionStatus = ConnectionEntity.State.IGNORED)
        val missingLeaseholdConsentUser =
            newUserEntity(id = "missingLeaseholdConsentUser").copy(connectionStatus = ConnectionEntity.State.MISSING_LEGALHOLD_CONSENT)
        val deletedUser = newUserEntity(id = "deletedUser").copy(connectionStatus = ConnectionEntity.State.ACCEPTED, deleted = true)

        userDAO.insertOrIgnoreUsers(
            listOf(
                connectedUser1,
                connectedUser2,
                pendingUser,
                blockedUser,
                notConnectedUser,
                ignoredUser,
                missingLeaseholdConsentUser,
                deletedUser
            )
        )

        searchDAO.getKnownContacts().also {
            assertEquals(2, it.size)
            assertEquals(connectedUser1.id, it[0].id)
            assertEquals(connectedUser2.id, it[1].id)
        }
    }

    @Test
    fun givenConnectedUser_whenGettingKnowUsersExcludingAConversation_thenReturnOnlyConnectedUsers() = runTest {

        val connectedUser1 = newUserEntity(id = "connectedUser1").copy(connectionStatus = ConnectionEntity.State.ACCEPTED)
        val connectedUser2 = newUserEntity(id = "connectedUser2").copy(connectionStatus = ConnectionEntity.State.ACCEPTED)
        val connectedPartOfConversation1 =
            newUserEntity(id = "connectedPartOfConversation1").copy(connectionStatus = ConnectionEntity.State.ACCEPTED)
        val connectedPartOfConversation2 =
            newUserEntity(id = "connectedPartOfConversation2").copy(connectionStatus = ConnectionEntity.State.ACCEPTED)
        val pendingUser = newUserEntity(id = "pendingUser").copy(connectionStatus = ConnectionEntity.State.PENDING)
        val blockedUser = newUserEntity(id = "blockedUser").copy(connectionStatus = ConnectionEntity.State.BLOCKED)
        val notConnectedUser = newUserEntity(id = "notConnectedUser").copy(connectionStatus = ConnectionEntity.State.NOT_CONNECTED)
        val ignoredUser = newUserEntity(id = "ignoredUser").copy(connectionStatus = ConnectionEntity.State.IGNORED)
        val missingLeaseholdConsentUser =
            newUserEntity(id = "missingLeaseholdConsentUser").copy(connectionStatus = ConnectionEntity.State.MISSING_LEGALHOLD_CONSENT)
        val deletedUser = newUserEntity(id = "deletedUser").copy(connectionStatus = ConnectionEntity.State.ACCEPTED, deleted = true)

        val conversationToExclude = newConversationEntity(id = "1")

        userDAO.insertOrIgnoreUsers(
            listOf(
                connectedUser1,
                connectedUser2,
                connectedPartOfConversation1,
                connectedPartOfConversation2,
                pendingUser,
                blockedUser,
                notConnectedUser,
                ignoredUser,
                missingLeaseholdConsentUser,
                deletedUser
            )
        )

        conversationDAO.insertConversation(conversationToExclude)
        memberDAO.insertMember(
            MemberEntity(
                connectedPartOfConversation1.id,
                MemberEntity.Role.Member
            ), conversationToExclude.id
        )
        memberDAO.insertMember(
            MemberEntity(
                connectedPartOfConversation2.id,
                MemberEntity.Role.Member
            ), conversationToExclude.id
        )
        searchDAO.getKnownContactsExcludingAConversation(
            conversationToExclude.id
        ).also {
            assertEquals(2, it.size)
            assertEquals(connectedUser1.id, it[0].id)
            assertEquals(connectedUser2.id, it[1].id)
        }
    }

    @Test
    fun givenUsers_whenSearching_thenOnlyReturnConnectedUsers() = runTest {
        val searchQuery = "searchQuery"
        val connectedUser1 = newUserEntity(id = "1").copy(name = "searchQuery", connectionStatus = ConnectionEntity.State.ACCEPTED)
        val connectedUser2 = newUserEntity(id = "2").copy(name = "qwerty", connectionStatus = ConnectionEntity.State.ACCEPTED)
        val pendingUser = newUserEntity(id = "pendingUser").copy(connectionStatus = ConnectionEntity.State.PENDING)
        val blockedUser = newUserEntity(id = "blockedUser").copy(connectionStatus = ConnectionEntity.State.BLOCKED)
        val notConnectedUser = newUserEntity(id = "notConnectedUser").copy(connectionStatus = ConnectionEntity.State.NOT_CONNECTED)
        val ignoredUser = newUserEntity(id = "ignoredUser").copy(connectionStatus = ConnectionEntity.State.IGNORED)
        val missingLeaseholdConsentUser =
            newUserEntity(id = "missingLeaseholdConsentUser").copy(connectionStatus = ConnectionEntity.State.MISSING_LEGALHOLD_CONSENT)
        val deletedUser = newUserEntity(id = "deletedUser").copy(connectionStatus = ConnectionEntity.State.ACCEPTED, deleted = true)

        userDAO.insertOrIgnoreUsers(
            listOf(
                connectedUser1,
                connectedUser2,
                pendingUser,
                blockedUser,
                notConnectedUser,
                ignoredUser,
                missingLeaseholdConsentUser,
                deletedUser
            )
        )

        searchDAO.searchList(searchQuery).also {
            assertEquals(1, it.size)
            assertEquals(connectedUser1.id, it[0].id)
        }
    }

    @Test
    fun givenUsers_whenSearchingAndExcludingAConversation_thenOnlyReturnConnectedUsersThatAreNotMembers() = runTest {
        val searchQuery = "searchQuery"
        val connectedUser1 = newUserEntity(id = "1").copy(name = searchQuery, connectionStatus = ConnectionEntity.State.ACCEPTED)
        val connectedUser2 = newUserEntity(id = "2").copy(name = searchQuery, connectionStatus = ConnectionEntity.State.ACCEPTED)
        val pendingUser = newUserEntity(id = "pendingUser").copy(connectionStatus = ConnectionEntity.State.PENDING)
        val blockedUser = newUserEntity(id = "blockedUser").copy(connectionStatus = ConnectionEntity.State.BLOCKED)
        val notConnectedUser = newUserEntity(id = "notConnectedUser").copy(connectionStatus = ConnectionEntity.State.NOT_CONNECTED)
        val ignoredUser = newUserEntity(id = "ignoredUser").copy(connectionStatus = ConnectionEntity.State.IGNORED)
        val missingLeaseholdConsentUser =
            newUserEntity(id = "missingLeaseholdConsentUser").copy(connectionStatus = ConnectionEntity.State.MISSING_LEGALHOLD_CONSENT)
        val deletedUser = newUserEntity(id = "deletedUser").copy(connectionStatus = ConnectionEntity.State.ACCEPTED, deleted = true)

        val conversation = newConversationEntity(id = "1")

        userDAO.insertOrIgnoreUsers(
            listOf(
                connectedUser1,
                connectedUser2,
                pendingUser,
                blockedUser,
                notConnectedUser,
                ignoredUser,
                missingLeaseholdConsentUser,
                deletedUser
            )
        )
        conversationDAO.insertConversation(conversation)
        memberDAO.insertMember(
            MemberEntity(
                connectedUser1.id,
                MemberEntity.Role.Member
            ), conversation.id
        )

        searchDAO.searchListExcludingAConversation(conversation.id, searchQuery).also {
            assertEquals(1, it.size)
            assertEquals(connectedUser2.id, it[0].id)
        }
    }

    @Test
    fun givenConnectedUser_whenSearchingByHandle_thenReturnOnlyConnectedUsers() = runTest {
        val searchQuery = "searchQuery"
        val connectedUser1 = newUserEntity(id = "1").copy(handle = "searchQuery", connectionStatus = ConnectionEntity.State.ACCEPTED)
        val connectedUser2 = newUserEntity(id = "2").copy(handle = "qwerty", connectionStatus = ConnectionEntity.State.ACCEPTED)
        val pendingUser = newUserEntity(id = "pendingUser").copy(connectionStatus = ConnectionEntity.State.PENDING)
        val blockedUser = newUserEntity(id = "blockedUser").copy(connectionStatus = ConnectionEntity.State.BLOCKED)
        val notConnectedUser = newUserEntity(id = "notConnectedUser").copy(connectionStatus = ConnectionEntity.State.NOT_CONNECTED)
        val ignoredUser = newUserEntity(id = "ignoredUser").copy(connectionStatus = ConnectionEntity.State.IGNORED)
        val missingLeaseholdConsentUser =
            newUserEntity(id = "missingLeaseholdConsentUser").copy(connectionStatus = ConnectionEntity.State.MISSING_LEGALHOLD_CONSENT)
        val deletedUser = newUserEntity(id = "deletedUser").copy(connectionStatus = ConnectionEntity.State.ACCEPTED, deleted = true)

        userDAO.insertOrIgnoreUsers(
            listOf(
                connectedUser1,
                connectedUser2,
                pendingUser,
                blockedUser,
                notConnectedUser,
                ignoredUser,
                missingLeaseholdConsentUser,
                deletedUser
            )
        )

        searchDAO.handleSearch(searchQuery).also {
            assertEquals(1, it.size)
            assertEquals(connectedUser1.id, it[0].id)
        }
    }

    @Test
    fun givenUsers_whenSearchingVByHandleAndExcludingAConversation_thenOnlyReturnConnectedUsersThatAreNotMembers() = runTest {
        val searchQuery = "searchQuery"
        val connectedUser1 = newUserEntity(id = "1").copy(handle = searchQuery, connectionStatus = ConnectionEntity.State.ACCEPTED)
        val connectedUser2 = newUserEntity(id = "2").copy(handle = searchQuery, connectionStatus = ConnectionEntity.State.ACCEPTED)
        val pendingUser = newUserEntity(id = "pendingUser").copy(connectionStatus = ConnectionEntity.State.PENDING)
        val blockedUser = newUserEntity(id = "blockedUser").copy(connectionStatus = ConnectionEntity.State.BLOCKED)
        val notConnectedUser = newUserEntity(id = "notConnectedUser").copy(connectionStatus = ConnectionEntity.State.NOT_CONNECTED)
        val ignoredUser = newUserEntity(id = "ignoredUser").copy(connectionStatus = ConnectionEntity.State.IGNORED)
        val missingLeaseholdConsentUser =
            newUserEntity(id = "missingLeaseholdConsentUser").copy(connectionStatus = ConnectionEntity.State.MISSING_LEGALHOLD_CONSENT)
        val deletedUser = newUserEntity(id = "deletedUser").copy(connectionStatus = ConnectionEntity.State.ACCEPTED, deleted = true)

        val conversation = newConversationEntity(id = "1")

        userDAO.insertOrIgnoreUsers(
            listOf(
                connectedUser1,
                connectedUser2,
                pendingUser,
                blockedUser,
                notConnectedUser,
                ignoredUser,
                missingLeaseholdConsentUser,
                deletedUser
            )
        )
        conversationDAO.insertConversation(conversation)
        memberDAO.insertMember(
            MemberEntity(
                connectedUser1.id,
                MemberEntity.Role.Member
            ), conversation.id
        )

        searchDAO.handleSearchExcludingAConversation(searchQuery, conversation.id).also {
            assertEquals(1, it.size)
            assertEquals(connectedUser2.id, it[0].id)
        }
    }
}
