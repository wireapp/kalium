package com.wire.kalium.persistence.dao.call

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CallDAOTest : BaseDatabaseTest() {

    private lateinit var callDAO: CallDAO

    @BeforeTest
    fun setUp() {
        deleteDatabase()
        val db = createDatabase()
        callDAO = db.callDAO
    }

    @Test
    fun givenOpenCalls_whenClosingOpenCalls_thenOpenCallIsClosed() = runTest {
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
            conversationType = ConversationEntity.Type.GROUP
        )

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
}
