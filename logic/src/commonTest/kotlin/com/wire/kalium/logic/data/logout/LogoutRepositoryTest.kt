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

package com.wire.kalium.logic.data.logout

import app.cash.turbine.test
import com.wire.kalium.network.api.base.authenticated.logout.LogoutApi
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.conversation.PendingSubconversationProposalDAO
import com.wire.kalium.persistence.dao.conversation.PendingSubconversationProposalEntity
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.client.ClientRegistrationStorageImpl
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import kotlin.time.Instant
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import dev.mokkery.MockMode
import dev.mokkery.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class LogoutRepositoryTest {

    @Test
    fun givenLogoutFlowIsBeingObserved_whenInvokingOnLogout_thenFlowShouldEmit() = runTest {
        // Given
        val reason = LogoutReason.SELF_HARD_LOGOUT

        val (_, logoutRepository) = Arrangement().arrange()

        logoutRepository.observeLogout().test {

            // When
            logoutRepository.onLogout(reason)

            // Then
            assertEquals(reason, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenOnLogoutWasNotInvoked_whenObservingLogoutFlow_thenNoEventsShouldBePresent() = runTest {
        // Given - NOOP

        val (_, logoutRepository) = Arrangement().arrange()

        // When
        logoutRepository.observeLogout().test {

            // Then
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenFailureHappensOnLogoutReasonCollection_whenCollectingAgain_thenShouldEmitNewValues() = runTest {
        val (_, logoutRepository) = Arrangement().arrange()

        logoutRepository.onLogout(LogoutReason.DELETED_ACCOUNT)
        try {
            logoutRepository.observeLogout().collect { throw IllegalStateException() }
        } catch (ignored: Throwable) {
            // Ignored, really
        }

        logoutRepository.observeLogout().test {
            logoutRepository.onLogout(LogoutReason.SELF_HARD_LOGOUT)
            assertEquals(LogoutReason.SELF_HARD_LOGOUT, awaitItem())
            logoutRepository.onLogout(LogoutReason.SESSION_EXPIRED)
            assertEquals(LogoutReason.SESSION_EXPIRED, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun clearingClientMetadataRemovesSqlProposalsAndLegacyWithoutLosingRetainedClientId() =
        runTest(TestKaliumDispatcher.default) {
            val database = TestUserDatabase(UserIDEntity("proposal-client-reset", "test"), TestKaliumDispatcher.default)
            try {
                val db = database.builder
                val queue = db.pendingSubconversationProposalDAO
                val record = PendingSubconversationProposalEntity("group", Instant.DISTANT_PAST, "token")
                queue.schedule(record)
                queue.schedule(record.copy(groupId = "marked-group"))
                queue.markForRetirement(record.copy(groupId = "marked-group"))
                db.metadataDAO.insertValue("malformed legacy", "pending-subconversation-proposals-v1")
                db.metadataDAO.insertValue("retained-client", ClientRegistrationStorageImpl.RETAINED_CLIENT_ID_KEY)
                val repository = LogoutDataSource(mock(), db.metadataDAO, queue)

                repository.clearClientRelatedLocalMetadata()

                assertTrue(queue.observe().first().isEmpty())
                assertNull(db.metadataDAO.valueByKey("pending-subconversation-proposals-v1"))
                assertEquals("retained-client", db.metadataDAO.valueByKey(ClientRegistrationStorageImpl.RETAINED_CLIENT_ID_KEY))
            } finally {
                database.builder.close()
                database.delete()
            }
        }

    private class Arrangement {
        private val logoutApi = mock<LogoutApi>(mode = MockMode.autoUnit)
        private val metadataDAO = mock<MetadataDAO>(mode = MockMode.autoUnit)

        private val logoutDataSource = LogoutDataSource(logoutApi, metadataDAO, mock<PendingSubconversationProposalDAO>(mode = MockMode.autoUnit))

        fun arrange() = this to logoutDataSource

    }

}
