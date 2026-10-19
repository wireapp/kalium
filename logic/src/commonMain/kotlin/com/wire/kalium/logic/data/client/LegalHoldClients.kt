/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.data.client

import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.getOrFail
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.persistence.dao.client.ClientDAO
import com.wire.kalium.persistence.dao.client.DeviceTypeEntity

/** Required reads and refreshes for durable legal hold transitions; failures must prevent acknowledgement. */
internal interface LegalHoldClients {
    suspend fun isEnabled(userId: UserId): Boolean
    suspend fun refresh(userId: UserId)
}

internal class LegalHoldClientsImpl(
    private val selfUserId: UserId,
    private val clients: ClientDAO,
    private val api: ClientApi,
    private val remote: ClientRemoteRepository,
    private val mapper: ClientMapper = MapperProvider.clientMapper(),
) : LegalHoldClients {
    override suspend fun isEnabled(userId: UserId): Boolean = wrapStorageRequest {
        clients.getClientsOfUserByQualifiedID(userId.toDao()).any { it.deviceType == DeviceTypeEntity.LegalHold }
    }.getOrFail { throw KaliumSyncException("Legal hold clients read failed", it) }

    override suspend fun refresh(userId: UserId) {
        val fetched = if (userId == selfUserId) {
            wrapApiRequest { api.fetchSelfUserClient() }.getOrFail {
                throw KaliumSyncException("Legal hold self clients refresh failed", it)
            }.map { mapper.toInsertClientParam(it, userId.toApi()) }
        } else {
            val response = remote.fetchOtherUserClients(listOf(userId)).getOrFail {
                throw KaliumSyncException("Legal hold peer clients refresh failed", it)
            }
            val list = checkNotNull(response[userId.toApi()]) { "Legal hold refresh omitted requested user" }
            mapper.toInsertClientParam(list, userId.toApi())
        }
        wrapStorageRequest {
            if (fetched.isEmpty()) clients.deleteClientsOfUserByQualifiedID(userId.toDao())
            else clients.insertClientsAndRemoveRedundant(fetched)
        }.getOrFail { throw KaliumSyncException("Legal hold clients persistence failed", it) }
    }
}
