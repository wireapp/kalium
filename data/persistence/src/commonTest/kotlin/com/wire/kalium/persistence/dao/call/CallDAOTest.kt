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

import app.cash.turbine.test
import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.call.CallDAOTest.Companion.callEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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

    @Test
    fun givenLastCallIsActive_whenObservingLastActiveCall_thenReturnActiveCall() = runTest {
        // given
        val conversationId = callEntity.conversationId
        val previousCall = callEntity.copy(id = "id1", status = CallEntity.Status.CLOSED)
        val lastCall = callEntity.copy(id = "id2", status = CallEntity.Status.ESTABLISHED)
        callDAO.insertCall(previousCall)
        callDAO.insertCall(lastCall)
        // when
        callDAO.observeLastActiveCallByConversationId(conversationId).test {
            // then
            assertNotNull(awaitItem()).also {
                assertEquals(lastCall, it)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenLastCallIsNotActive_whenObservingLastActiveCall_thenReturnNull() = runTest {
        // given
        val conversationId = callEntity.conversationId
        val previousCall = callEntity.copy(id = "id1", status = CallEntity.Status.CLOSED)
        val lastCall = callEntity.copy(id = "id2", status = CallEntity.Status.CLOSED)
        callDAO.insertCall(previousCall)
        callDAO.insertCall(lastCall)
        // when
        callDAO.observeLastActiveCallByConversationId(conversationId).test {
            // then
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenLastCallIsUpdated_whenObservingLastActiveCall_thenReturnUpdatedActiveCall() = runTest {
        // given
        val conversationId = callEntity.conversationId
        val previousCall = callEntity.copy(id = "id1", status = CallEntity.Status.CLOSED)
        val lastCall = callEntity.copy(id = "id2", status = CallEntity.Status.ANSWERED)
        val updatedLastCall = lastCall.copy(status = CallEntity.Status.ESTABLISHED)
        callDAO.insertCall(previousCall)
        callDAO.insertCall(lastCall)
        // when
        callDAO.observeLastActiveCallByConversationId(conversationId).test {
            // then
            assertNotNull(awaitItem()).also {
                assertEquals(lastCall, it)
            }

            callDAO.updateLastCallStatusByConversationId(updatedLastCall.status, conversationId)
            assertNotNull(awaitItem()).also {
                assertEquals(updatedLastCall, it)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenLastCallStopsBeingActive_whenObservingLastActiveCall_thenReturnNull() = runTest {
        // given
        val conversationId = callEntity.conversationId
        val previousCall = callEntity.copy(id = "id1", status = CallEntity.Status.CLOSED)
        val lastCall = callEntity.copy(id = "id2", status = CallEntity.Status.ESTABLISHED)
        callDAO.insertCall(previousCall)
        callDAO.insertCall(lastCall)
        // when
        callDAO.observeLastActiveCallByConversationId(conversationId).test {
            // then
            assertNotNull(awaitItem()).also {
                assertEquals(lastCall, it)
            }

            callDAO.updateLastCallStatusByConversationId(CallEntity.Status.CLOSED, conversationId)
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenMultipleCallsForConversation_whenGettingLastCallByConversationId_thenReturnLatestCall() = runTest {
        // given
        val conversationId = callEntity.conversationId
        val previousCall = callEntity.copy(id = "id1", status = CallEntity.Status.CLOSED)
        val lastCall = callEntity.copy(
            id = "id2",
            status = CallEntity.Status.ESTABLISHED,
            type = CallEntity.Type.MLS_CONFERENCE
        )
        val otherConversationCall = callEntity.copy(
            id = "id3",
            conversationId = conversationId.copy(value = "otherConvId"),
            status = CallEntity.Status.STILL_ONGOING
        )
        callDAO.insertCall(previousCall)
        callDAO.insertCall(lastCall)
        callDAO.insertCall(otherConversationCall)

        // when
        val result = callDAO.getLastCallByConversationId(conversationId)

        // then
        assertEquals(lastCall, result)
    }

    @Test
    fun givenNoActiveCalls_whenObservingActiveCalls_thenReturnEmptyList() = runTest {
        // given
        nonActiveCalls.forEach {
            callDAO.insertCall(it)
        }

        // when
        callDAO.observeActiveCalls().test {
            // then
            assertEquals(emptyList(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenNonActiveAndActiveCalls_whenObservingActiveCalls_thenReturnOnlyActiveCalls() = runTest {
        // given
        (nonActiveCalls + activeCalls).forEach {
            callDAO.insertCall(it)
        }

        // when
        callDAO.observeActiveCalls().test {
            // then
            assertEquals(activeCalls.toSet(), awaitItem().toSet())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenNonActiveAndActiveCalls_andSomeCallIsUpdated_whenObservingActiveCalls_thenReturnUpdatedActiveCalls() = runTest {
        // given
        val callToUpdate = callEntity.copy(id = "id_to_update", status = CallEntity.Status.ESTABLISHED) // now it's active
        (nonActiveCalls + activeCalls + callToUpdate).forEach {
            callDAO.insertCall(it)
        }

        // when
        callDAO.observeActiveCalls().test {
            // then
            assertEquals((activeCalls + callToUpdate).toSet(), awaitItem().toSet())

            callDAO.updateLastCallStatusByConversationId(CallEntity.Status.CLOSED, callToUpdate.conversationId) // now it's not active
            assertEquals(activeCalls.toSet(), awaitItem().toSet())
            cancelAndIgnoreRemainingEvents()
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

        val nonActiveCalls = listOf(
            CallEntity.Status.CLOSED,
            CallEntity.Status.REJECTED,
            CallEntity.Status.MISSED,
            CallEntity.Status.CLOSED_INTERNALLY
        ).map(CallEntity.Status::createCallEntityFromStatus)

        val activeCalls = listOf(
            CallEntity.Status.STARTED,
            CallEntity.Status.INCOMING,
            CallEntity.Status.ANSWERED,
            CallEntity.Status.ESTABLISHED,
            CallEntity.Status.STILL_ONGOING
        ).map(CallEntity.Status::createCallEntityFromStatus)
    }
}

private fun CallEntity.Status.createCallEntityFromStatus(): CallEntity = callEntity.copy(
    id = "id_${this.ordinal}",
    conversationId = QualifiedIDEntity("conversation_id_${this.ordinal}", "domain"),
    status = this
)
