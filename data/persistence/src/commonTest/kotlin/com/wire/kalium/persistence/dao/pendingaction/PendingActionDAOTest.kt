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
import com.wire.kalium.persistence.dao.UserIDEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PendingActionDAOTest : BaseDatabaseTest() {

    private lateinit var pendingActionDAO: PendingActionDAO
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")
    private val actionType = PendingActionType.RESOLVE_ONE_ON_ONE_CONVERSATION

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        val db = createDatabase(selfUserId, encryptedDBSecret, true)
        pendingActionDAO = db.pendingActionDAO
    }

    @Test
    fun givenNoPendingActions_whenGettingByType_thenReturnEmptyList() = runTest(dispatcher) {
        val result = pendingActionDAO.getByActionType(actionType)

        assertTrue(result.isEmpty())
    }

    @Test
    fun givenSameTypeAndKey_whenUpsertingTwice_thenKeepLatestValues() = runTest(dispatcher) {
        pendingActionDAO.upsert(
            actionType = actionType,
            actionKey = "user-1@wire.com",
            payload = """{"value":1}""",
            createdAt = 10L
        )
        pendingActionDAO.upsert(
            actionType = actionType,
            actionKey = "user-1@wire.com",
            payload = """{"value":2}""",
            createdAt = 20L
        )

        val result = pendingActionDAO.getByActionType(actionType)

        assertEquals(1, result.size)
        assertEquals("user-1@wire.com", result.first().actionKey)
        assertEquals("""{"value":2}""", result.first().payload)
        assertEquals(20L, result.first().createdAt)
    }

    @Test
    fun givenMultiplePendingActions_whenGettingByType_thenReturnOrderedByCreatedAt() = runTest(dispatcher) {
        pendingActionDAO.upsert(
            actionType = actionType,
            actionKey = "user-2@wire.com",
            payload = """{"value":2}""",
            createdAt = 200L
        )
        pendingActionDAO.upsert(
            actionType = actionType,
            actionKey = "user-1@wire.com",
            payload = """{"value":1}""",
            createdAt = 100L
        )

        val result = pendingActionDAO.getByActionType(actionType)

        assertEquals(listOf("user-1@wire.com", "user-2@wire.com"), result.map { it.actionKey })
    }

    @Test
    fun givenExistingPendingActions_whenDeletingSubset_thenDeleteOnlyMatchingKeys() = runTest(dispatcher) {
        pendingActionDAO.upsert(
            actionType = actionType,
            actionKey = "user-1@wire.com",
            payload = null,
            createdAt = 1L
        )
        pendingActionDAO.upsert(
            actionType = actionType,
            actionKey = "user-2@wire.com",
            payload = null,
            createdAt = 2L
        )

        pendingActionDAO.deleteByActionTypeAndKeys(actionType, listOf("user-1@wire.com"))

        val result = pendingActionDAO.getByActionType(actionType)
        assertEquals(listOf("user-2@wire.com"), result.map { it.actionKey })
    }

    @Test
    fun givenExistingPendingActions_whenDeletingByType_thenDeleteAll() = runTest(dispatcher) {
        pendingActionDAO.upsert(
            actionType = actionType,
            actionKey = "user-1@wire.com",
            payload = null,
            createdAt = 1L
        )
        pendingActionDAO.upsert(
            actionType = actionType,
            actionKey = "user-2@wire.com",
            payload = null,
            createdAt = 2L
        )

        pendingActionDAO.deleteByActionType(actionType)

        val result = pendingActionDAO.getByActionType(actionType)
        assertTrue(result.isEmpty())
    }
}
