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

package com.wire.kalium.persistence.dao.call

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CallDAOTest : BaseDatabaseTest() {

    private lateinit var callDAO: CallDAO
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        val db = createDatabase(selfUserId, encryptedDBSecret, true)
        callDAO = db.callDAO
    }

    @Test
    fun givenOpenCalls_whenClosingOpenCalls_thenOpenCallIsClosed() = runTest {

        callDAO.insertCall(call = callEntity)
        callDAO.insertCall(
            call = callEntity.copy(
                conversationId = convId.copy(value = "convId2"),
                id = "$callId 2",
                status = CallEntity.Status.STILL_ONGOING
            )
        )
        callDAO.insertCall(
            call = callEntity.copy(
                conversationId = convId.copy(value = "convId3"),
                id = "$callId 3",
                status = CallEntity.Status.ESTABLISHED
            )
        )
        callDAO.insertCall(
            call = callEntity.copy(
                conversationId = convId.copy(value = "convId4"),
                id = "$callId 4",
                status = CallEntity.Status.ANSWERED
            )
        )
        callDAO.insertCall(
            call = callEntity.copy(
                conversationId = convId.copy(value = "convId5"),
                id = "$callId 5",
                status = CallEntity.Status.INCOMING
            )
        )

        // when
        callDAO.updateOpenCallsToClosedStatus()

        val calls = callDAO.observeCalls()

        // then
        assertEquals(calls.first()[0].status, CallEntity.Status.CLOSED)
        assertEquals(calls.first()[1].status, CallEntity.Status.CLOSED)
        assertEquals(calls.first()[2].status, CallEntity.Status.CLOSED)
        assertEquals(calls.first()[3].status, CallEntity.Status.CLOSED)
        assertEquals(calls.first()[4].status, CallEntity.Status.CLOSED)
    }

    @Test
    fun givenOutgoingCall_whenObserveOutgoingCalls_thenOutgoingCallIsReturned() = runTest {
        callDAO.insertCall(
            call = callEntity.copy(
                conversationId = convId.copy(value = "convId2"),
                id = "$callId 2",
                status = CallEntity.Status.STILL_ONGOING
            )
        )
        callDAO.insertCall(call = callEntity)
        callDAO.insertCall(
            call = callEntity.copy(
                conversationId = convId.copy(value = "convId5"),
                id = "$callId 5",
                status = CallEntity.Status.INCOMING
            )
        )

        val outgoingCalls = callDAO.observeOutgoingCalls()

        outgoingCalls.first().let {
            assertEquals(1, it.size)
            assertEquals(callEntity, it[0])
        }
    }

    companion object {
        // given
        val convId = QualifiedIDEntity(
            value = "convId",
            domain = "convDomain"
        )
        val callId = "callId"
        val callEntity = CallEntity(
            conversationId = convId,
            id = callId,
            status = CallEntity.Status.STARTED,
            callerId = "callerId",
            conversationType = ConversationEntity.Type.GROUP,
            type = CallEntity.Type.CONFERENCE
        )
    }
}
