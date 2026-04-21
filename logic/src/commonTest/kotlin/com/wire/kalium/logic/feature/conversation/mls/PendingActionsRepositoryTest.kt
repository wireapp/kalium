/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.conversation.mls

import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.persistence.dao.pendingaction.PendingActionDAO
import com.wire.kalium.persistence.dao.pendingaction.PendingActionEntity
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PendingActionsRepositoryTest {

    @Test
    fun givenUserId_whenEnqueuePendingOneOnOneResolution_thenUpsertIsCalledWithOneOnOneActionData() = runTest {
        val (arrangement, repository) = Arrangement().arrange()

        repository.enqueuePendingOneOnOneResolution(TestUser.OTHER_USER_ID)

        coVerify {
            arrangement.pendingActionDAO.upsert(
                actionType = eq(OneOnOneResolutionPendingAction.actionType),
                actionKey = eq(OneOnOneResolutionPendingAction.actionKey(TestUser.OTHER_USER_ID)),
                payload = eq(OneOnOneResolutionPendingAction.payload(TestUser.OTHER_USER_ID)),
                createdAt = any()
            )
        }.wasInvoked(once)
    }

    @Test
    fun givenPendingRows_whenGettingPendingOneOnOneResolutions_thenReturnsUsersParsedFromActionKey() = runTest {
        val user1 = TestUser.OTHER_USER_ID
        val user2 = TestUser.OTHER_USER_ID_2
        val (arrangement, repository) = Arrangement()
            .withPendingRows(
                PendingActionEntity(OneOnOneResolutionPendingAction.actionKey(user1), "not-json", 1L),
                PendingActionEntity("invalid-action-key", OneOnOneResolutionPendingAction.payload(user1), 2L),
                PendingActionEntity(OneOnOneResolutionPendingAction.actionKey(user2), null, 3L),
            )
            .arrange()

        val result = repository.getPendingOneOnOneResolutions()

        assertEquals(listOf(user1, user2), result)
        coVerify {
            arrangement.pendingActionDAO.getByActionType(eq(OneOnOneResolutionPendingAction.actionType))
        }.wasInvoked(once)
        coVerify { arrangement.pendingActionDAO.deleteByActionTypeAndKeys(any(), any()) }.wasNotInvoked()
    }

    @Test
    fun givenPendingUserIds_whenAcknowledging_thenDeletesByActionTypeAndMappedDistinctKeys() = runTest {
        val (arrangement, repository) = Arrangement().arrange()
        val user1 = TestUser.OTHER_USER_ID
        val user2 = TestUser.OTHER_USER_ID_2

        repository.acknowledgePendingOneOnOneResolutions(listOf(user1, user1, user2))

        coVerify {
            arrangement.pendingActionDAO.deleteByActionTypeAndKeys(
                actionType = eq(OneOnOneResolutionPendingAction.actionType),
                actionKeys = eq(
                    listOf(
                        OneOnOneResolutionPendingAction.actionKey(user1),
                        OneOnOneResolutionPendingAction.actionKey(user2)
                    )
                )
            )
        }.wasInvoked(once)
    }

    @Test
    fun givenNoUserIds_whenAcknowledging_thenNothingIsDeleted() = runTest {
        val (arrangement, repository) = Arrangement().arrange()

        repository.acknowledgePendingOneOnOneResolutions(emptyList())

        coVerify {
            arrangement.pendingActionDAO.deleteByActionTypeAndKeys(any(), any())
        }.wasNotInvoked()
    }

    private class Arrangement {
        val pendingActionDAO = mock(PendingActionDAO::class)
        private val repository = PersistentPendingActionsRepository(pendingActionDAO)

        suspend fun withPendingRows(vararg rows: PendingActionEntity) = apply {
            coEvery {
                pendingActionDAO.getByActionType(eq(OneOnOneResolutionPendingAction.actionType))
            }.returns(rows.toList())
        }

        suspend fun arrange(): Pair<Arrangement, PendingActionsRepository> = apply {
            coEvery { pendingActionDAO.upsert(any(), any(), any(), any()) }.returns(Unit)
            coEvery { pendingActionDAO.deleteByActionTypeAndKeys(any(), any()) }.returns(Unit)
        } to repository
    }
}
