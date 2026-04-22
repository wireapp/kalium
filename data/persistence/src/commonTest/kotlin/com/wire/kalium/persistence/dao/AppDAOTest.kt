/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.member.MemberEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.utils.stubs.newAppEntity
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppDAOTest : BaseDatabaseTest() {

    private val app1 = newAppEntity(id = "1")
    private val app2 = newAppEntity(id = "2")
    private val app3 = newAppEntity(id = "3")

    lateinit var db: UserDatabaseBuilder

    private val selfUserId = UserIDEntity("selfValue", "wire.com")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        db = createDatabase(selfUserId, encryptedDBSecret, true)
    }

    @Test
    fun givenApp_thenAppCanBeInserted() = runTest(dispatcher) {
        db.appDAO.insert(app1)
        val result = db.appDAO.byId(app1.id)
        assertEquals(result, app1)
    }

    @Test
    fun givenListOfApps_thenMultipleAppsCanBeInsertedAtOnce() = runTest(dispatcher) {
        db.appDAO.upsertApps(listOf(app1, app2))

        val result1 = db.appDAO.byId(app1.id)
        val result2 = db.appDAO.byId(app2.id)

        assertEquals(result1, app1)
        assertEquals(result2, app2)
    }

    @Test
    fun givenExistingAppInConversation_whenCheckingIfAppIsMember_thenReturn() = runTest(dispatcher) {
        val selfUser = newUserEntity(selfUserId)
        val groupConversation =
            newConversationEntity(
                id = ConversationIDEntity("conversationId", "domain")
            ).copy(
                type = ConversationEntity.Type.GROUP,
                accessRole = listOf(
                    ConversationEntity.AccessRole.NON_TEAM_MEMBER,
                    ConversationEntity.AccessRole.TEAM_MEMBER,
                    ConversationEntity.AccessRole.GUEST,
                    ConversationEntity.AccessRole.SERVICE,
                )
            )

        db.userDAO.upsertUser(selfUser)
        db.conversationDAO.insertConversation(groupConversation)
        db.memberDAO.insertMember(MemberEntity(selfUser.id, MemberEntity.Role.Admin), groupConversation.id)
        db.memberDAO.insertMember(MemberEntity(app1.id, MemberEntity.Role.Admin), groupConversation.id)

        val result = db.appDAO.observeIsAppMember(app1.id, groupConversation.id).first()
        assertEquals(result, app1.id)

        val appIsNotMemberResult = db.appDAO.observeIsAppMember(app2.id, groupConversation.id).first()
        assertNull(appIsNotMemberResult)
    }

    @Test
    fun givenExistingApps_whenObservingAllApps_thenReturnListOfApps() = runTest(dispatcher) {
        db.appDAO.upsertApps(listOf(app1, app2))

        val result = db.appDAO.observeAllApps().first()

        assertEquals(result.size, 2)
        assertEquals(result.first(), app1)
        assertEquals(result.last(), app2)
    }

    @Test
    fun givenExistingApps_whenSearchingForAppsByName_thenReturnListOfFoundApps() = runTest(dispatcher) {
        db.appDAO.upsertApps(listOf(app1, app3))

        val result = db.appDAO.searchAppsByName(app3.name).first()

        assertEquals(result.size, 1)
        assertEquals(result.first(), app3)

        val failureResult = db.appDAO.searchAppsByName(app2.name).first()

        assertEquals(failureResult.size, 0)
        assertEquals(failureResult, emptyList())
    }
}
