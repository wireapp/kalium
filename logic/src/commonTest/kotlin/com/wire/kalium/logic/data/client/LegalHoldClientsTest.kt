/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.data.client

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.client.ClientDAO
import com.wire.kalium.persistence.dao.client.ClientTypeEntity
import com.wire.kalium.persistence.dao.client.DeviceTypeEntity
import com.wire.kalium.persistence.dao.client.InsertClientParam
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegalHoldClientsTest {
    @Test
    fun emptyRefreshRemovesLastLegalHoldClientForSelfAndPeer() = runTest {
        val database = TestUserDatabase(SELF.toDao(), StandardTestDispatcher(testScheduler))
        try {
            val db = database.builder
            val api = mock<ClientApi>()
            val remote = mock<ClientRemoteRepository>()
            everySuspend { api.fetchSelfUserClient() } returns NetworkResponse.Success(emptyList(), emptyMap(), 200)
            everySuspend { remote.fetchOtherUserClients(any()) } returns Either.Right(mapOf(PEER.toApi() to emptyList()))
            val clients = LegalHoldClientsImpl(SELF, db.clientDAO, api, remote)
            for (user in listOf(SELF, PEER)) {
                db.userDAO.upsertUser(TestUser.ENTITY.copy(id = user.toDao(), team = null, previewAssetId = null, completeAssetId = null))
                db.clientDAO.insertClient(InsertClientParam(user.toDao(), "hold", DeviceTypeEntity.LegalHold,
                    ClientTypeEntity.LegalHold, null, null, null, null, null, false, false))
                assertTrue(clients.isEnabled(user))
                clients.refresh(user)
                assertFalse(clients.isEnabled(user))
            }
        } finally { database.builder.close(); database.delete() }
    }

    @Test
    fun networkFailureAndMissingRequestedUserAreNotSuccessfulRefreshes() = runTest {
        val remote = mock<ClientRemoteRepository>()
        val clients = LegalHoldClientsImpl(SELF, mock<ClientDAO>(), mock<ClientApi>(), remote)
        everySuspend { remote.fetchOtherUserClients(any()) } returns Either.Left(NetworkFailure.NoNetworkConnection(null))
        assertFailsWith<KaliumSyncException> { clients.refresh(PEER) }
        everySuspend { remote.fetchOtherUserClients(any()) } returns Either.Right(emptyMap())
        assertFailsWith<IllegalStateException> { clients.refresh(PEER) }
    }

    @Test
    fun storageReadAndWriteFailuresAreNotDisabledOrSuccessfulRefreshes() = runTest {
        val dao = mock<ClientDAO>()
        val remote = mock<ClientRemoteRepository>()
        val clients = LegalHoldClientsImpl(SELF, dao, mock<ClientApi>(), remote)
        everySuspend { dao.getClientsOfUserByQualifiedID(any()) } throws IllegalStateException("read failure")
        assertFailsWith<KaliumSyncException> { clients.isEnabled(PEER) }
        everySuspend { remote.fetchOtherUserClients(any()) } returns Either.Right(mapOf(PEER.toApi() to emptyList()))
        everySuspend { dao.deleteClientsOfUserByQualifiedID(any()) } throws IllegalStateException("write failure")
        assertFailsWith<KaliumSyncException> { clients.refresh(PEER) }
    }

    companion object {
        private val SELF = QualifiedID("legalhold-client-test", "test")
        private val PEER = QualifiedID("peer", "test")
    }
}
