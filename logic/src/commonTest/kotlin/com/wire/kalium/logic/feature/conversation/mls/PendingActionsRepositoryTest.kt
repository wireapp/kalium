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

import com.wire.kalium.logic.data.conversation.mls.PendingActionsRepository
import com.wire.kalium.logic.data.conversation.mls.PersistentPendingActionsRepository
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.persistence.dao.pendingaction.PendingActionDAO
import com.wire.kalium.persistence.dao.pendingaction.PendingActionEntity
import com.wire.kalium.persistence.dao.pendingaction.PendingActionType
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PendingActionsRepositoryTest {

    @Test
    fun givenUserId_whenEnqueuePendingOneOnOneResolution_thenUpsertIsCalledWithOneOnOneActionData() = runTest {
        val (arrangement, repository) = Arrangement().arrange()

        repository.enqueuePendingOneOnOneResolution(TestUser.OTHER_USER_ID)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.pendingActionDAO.upsert(
                actionType = eq(PendingActionType.RESOLVE_ONE_ON_ONE_CONVERSATION),
                qualifiedId = eq(TestUser.OTHER_USER_ID.toDao()),
                createdAt = any()
            )
        }
    }

    @Test
    fun givenPendingRows_whenGettingPendingOneOnOneResolutions_thenReturnsUsersFromQualifiedIds() = runTest {
        val user1 = TestUser.OTHER_USER_ID
        val user2 = TestUser.OTHER_USER_ID_2
        val (arrangement, repository) = Arrangement()
            .withPendingRows(
                PendingActionEntity(user1.toDao(), 1L),
                PendingActionEntity(user2.toDao(), 2L),
            )
            .arrange()

        val result = repository.getPendingOneOnOneResolutions()

        assertEquals(listOf(user1, user2), result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.pendingActionDAO.getByActionType(eq(PendingActionType.RESOLVE_ONE_ON_ONE_CONVERSATION))
        }
        verifySuspend(VerifyMode.not) { arrangement.pendingActionDAO.deleteByActionTypeAndIds(any(), any()) }
    }

    @Test
    fun givenPendingUserIds_whenAcknowledging_thenDeletesByActionTypeAndMappedDistinctIds() = runTest {
        val (arrangement, repository) = Arrangement().arrange()
        val user1 = TestUser.OTHER_USER_ID
        val user2 = TestUser.OTHER_USER_ID_2

        repository.acknowledgePendingOneOnOneResolutions(listOf(user1, user1, user2))

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.pendingActionDAO.deleteByActionTypeAndIds(
                actionType = eq(PendingActionType.RESOLVE_ONE_ON_ONE_CONVERSATION),
                qualifiedIds = eq(
                    listOf(
                        user1.toDao(),
                        user2.toDao()
                    )
                )
            )
        }
    }

    @Test
    fun givenNoUserIds_whenAcknowledging_thenNothingIsDeleted() = runTest {
        val (arrangement, repository) = Arrangement().arrange()

        repository.acknowledgePendingOneOnOneResolutions(emptyList())

        verifySuspend(VerifyMode.not) {
            arrangement.pendingActionDAO.deleteByActionTypeAndIds(any(), any())
        }
    }

    @Test
    fun givenConversationId_whenEnqueuePendingMLSGroupJoin_thenUpsertIsCalledWithGroupJoinActionData() = runTest {
        val (arrangement, repository) = Arrangement().arrange()

        repository.enqueuePendingMLSGroupJoin(TestConversation.ID)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.pendingActionDAO.upsert(
                actionType = eq(PendingActionType.JOIN_MLS_GROUP_CONVERSATION),
                qualifiedId = eq(TestConversation.ID.toDao()),
                createdAt = any()
            )
        }
    }

    @Test
    fun givenPendingRows_whenGettingPendingMLSGroupJoins_thenReturnsConversationsFromQualifiedIds() = runTest {
        val conversation1 = TestConversation.ID
        val conversation2 = TestConversation.id(2)
        val (arrangement, repository) = Arrangement()
            .withPendingGroupRows(
                PendingActionEntity(conversation1.toDao(), 1L),
                PendingActionEntity(conversation2.toDao(), 2L),
            )
            .arrange()

        val result = repository.getPendingMLSGroupJoins()

        assertEquals(listOf(conversation1, conversation2), result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.pendingActionDAO.getByActionType(eq(PendingActionType.JOIN_MLS_GROUP_CONVERSATION))
        }
        verifySuspend(VerifyMode.not) { arrangement.pendingActionDAO.deleteByActionTypeAndIds(any(), any()) }
    }

    @Test
    fun givenPendingConversationIds_whenAcknowledgingGroupJoins_thenDeletesByActionTypeAndMappedDistinctIds() = runTest {
        val (arrangement, repository) = Arrangement().arrange()
        val conversation1 = TestConversation.ID
        val conversation2 = TestConversation.id(2)

        repository.acknowledgePendingMLSGroupJoins(listOf(conversation1, conversation1, conversation2))

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.pendingActionDAO.deleteByActionTypeAndIds(
                actionType = eq(PendingActionType.JOIN_MLS_GROUP_CONVERSATION),
                qualifiedIds = eq(
                    listOf(
                        conversation1.toDao(),
                        conversation2.toDao()
                    )
                )
            )
        }
    }

    private class Arrangement {
        val pendingActionDAO = mock<PendingActionDAO>(mode = MockMode.autoUnit)
        private val repository = PersistentPendingActionsRepository(pendingActionDAO)

        suspend fun withPendingRows(vararg rows: PendingActionEntity) = apply {
            everySuspend {
                pendingActionDAO.getByActionType(eq(PendingActionType.RESOLVE_ONE_ON_ONE_CONVERSATION))
            } returns rows.toList()
        }

        suspend fun withPendingGroupRows(vararg rows: PendingActionEntity) = apply {
            everySuspend {
                pendingActionDAO.getByActionType(eq(PendingActionType.JOIN_MLS_GROUP_CONVERSATION))
            } returns rows.toList()
        }

        suspend fun arrange(): Pair<Arrangement, PendingActionsRepository> = apply {
            everySuspend { pendingActionDAO.upsert(any(), any(), any()) } returns Unit
            everySuspend { pendingActionDAO.deleteByActionTypeAndIds(any(), any()) } returns Unit
        } to repository
    }
}
