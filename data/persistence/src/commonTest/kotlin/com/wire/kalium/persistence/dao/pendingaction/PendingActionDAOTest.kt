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

package com.wire.kalium.persistence.dao.pendingaction

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PendingActionDAOTest : BaseDatabaseTest() {

    private lateinit var pendingActionDAO: PendingActionDAO
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")
    private val actionType = PendingActionType.RESOLVE_ONE_ON_ONE_CONVERSATION
    private val user1 = QualifiedIDEntity("user-1", "wire.com")
    private val user2 = QualifiedIDEntity("user-2", "wire.com")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        val db = createDatabase(selfUserId, encryptedDBSecret, true)
        pendingActionDAO = db.pendingActionDAO
    }

    @Test
    fun conversationClearRequestsSurviveReopenAndRecoveryActionAcknowledgement() = runTest(dispatcher) {
        val account = UserIDEntity("pending-clear", "wire.com")
        deleteDatabase(account)
        val database = createDatabase(account, encryptedDBSecret, true)
        try {
            database.writeMessageEffects {
                pendingActions.upsert(PendingActionType.CLEAR_CONVERSATION_CONTENT, user1, 100L)
                pendingActions.upsert(PendingActionType.DELETE_CONVERSATION_LOCALLY, user2, 200L)
                // Replaying an event upserts one action rather than duplicating work.
                pendingActions.upsert(PendingActionType.CLEAR_CONVERSATION_CONTENT, user1, 100L)
                pendingActions.upsert(PendingActionType.JOIN_MLS_GROUP_CONVERSATION, user1, 300L)
            }
        } finally {
            database.close()
        }
        val reopened = createDatabase(account, encryptedDBSecret, true)
        try {
            // Existing app recovery consumers only acknowledge their own action types.
            reopened.pendingActionDAO.deleteByActionType(PendingActionType.RESOLVE_ONE_ON_ONE_CONVERSATION)
            reopened.pendingActionDAO.deleteByActionTypeAndIds(PendingActionType.JOIN_MLS_GROUP_CONVERSATION, listOf(user1))
            assertEquals(
                listOf(PendingActionEntity(user1, 100L)),
                reopened.pendingActionDAO.getByActionType(PendingActionType.CLEAR_CONVERSATION_CONTENT),
            )
            assertEquals(
                listOf(PendingActionEntity(user2, 200L)),
                reopened.pendingActionDAO.getByActionType(PendingActionType.DELETE_CONVERSATION_LOCALLY),
            )
        } finally {
            reopened.close()
        }
    }

    @Test
    fun conversationClearActionRollsBackWithOtherReceivingEffects() = runTest(dispatcher) {
        val account = UserIDEntity("pending-clear-rollback", "wire.com")
        deleteDatabase(account)
        val database = createDatabase(account, encryptedDBSecret, true)
        try {
            assertFails {
                database.writeMessageEffects {
                    pendingActions.upsert(PendingActionType.CLEAR_CONVERSATION_CONTENT, user1, 100L)
                    error("Later notification effect failed")
                }
            }
            assertTrue(database.pendingActionDAO.getByActionType(PendingActionType.CLEAR_CONVERSATION_CONTENT).isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun givenNoPendingActions_whenGettingByType_thenReturnEmptyList() = runTest(dispatcher) {
        val result = pendingActionDAO.getByActionType(actionType)

        assertTrue(result.isEmpty())
    }

    @Test
    fun givenSameTypeAndId_whenUpsertingTwice_thenKeepLatestValues() = runTest(dispatcher) {
        pendingActionDAO.upsert(
            actionType = actionType,
            qualifiedId = user1,
            createdAt = 10L
        )
        pendingActionDAO.upsert(
            actionType = actionType,
            qualifiedId = user1,
            createdAt = 20L
        )

        val result = pendingActionDAO.getByActionType(actionType)

        assertEquals(1, result.size)
        assertEquals(user1, result.first().qualifiedId)
        assertEquals(20L, result.first().createdAt)
    }

    @Test
    fun givenMultiplePendingActions_whenGettingByType_thenReturnOrderedByCreatedAt() = runTest(dispatcher) {
        pendingActionDAO.upsert(
            actionType = actionType,
            qualifiedId = user2,
            createdAt = 200L
        )
        pendingActionDAO.upsert(
            actionType = actionType,
            qualifiedId = user1,
            createdAt = 100L
        )

        val result = pendingActionDAO.getByActionType(actionType)

        assertEquals(listOf(user1, user2), result.map { it.qualifiedId })
    }

    @Test
    fun givenExistingPendingActions_whenDeletingSubset_thenDeleteOnlyMatchingIds() = runTest(dispatcher) {
        pendingActionDAO.upsert(
            actionType = actionType,
            qualifiedId = user1,
            createdAt = 1L
        )
        pendingActionDAO.upsert(
            actionType = actionType,
            qualifiedId = user2,
            createdAt = 2L
        )

        pendingActionDAO.deleteByActionTypeAndIds(actionType, listOf(user1))

        val result = pendingActionDAO.getByActionType(actionType)
        assertEquals(listOf(user2), result.map { it.qualifiedId })
    }

    @Test
    fun givenExistingPendingActions_whenDeletingByType_thenDeleteAll() = runTest(dispatcher) {
        pendingActionDAO.upsert(
            actionType = actionType,
            qualifiedId = user1,
            createdAt = 1L
        )
        pendingActionDAO.upsert(
            actionType = actionType,
            qualifiedId = user2,
            createdAt = 2L
        )

        pendingActionDAO.deleteByActionType(actionType)

        val result = pendingActionDAO.getByActionType(actionType)
        assertTrue(result.isEmpty())
    }
}
