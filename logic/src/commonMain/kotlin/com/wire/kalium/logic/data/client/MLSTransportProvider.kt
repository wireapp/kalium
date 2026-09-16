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
package com.wire.kalium.logic.data.client

import com.wire.kalium.common.error.MLSTransportFailureSerialization
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapNetworkMlsFailureIfApplicable
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CommitBundle
import com.wire.kalium.cryptography.MLSTransporter
import com.wire.kalium.cryptography.MlsMessageRejectedException
import com.wire.kalium.logic.data.conversation.MLSCommitBundleMapper
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.sync.local.LocalEventRepository
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.persistence.dao.message.LocalId

internal interface MLSTransportProvider : MLSTransporter

internal class MLSTransportProviderImpl(
    private val selfUserId: UserId,
    private val mlsMessageApi: MLSMessageApi,
    private val localEventRepository: LocalEventRepository,
    private val mlsCommitBundleMapper: MLSCommitBundleMapper = MapperProvider.mlsCommitBundleMapper(),
) : MLSTransportProvider {

    override suspend fun sendCommitBundle(commitBundle: CommitBundle) {
        wrapApiRequest {
            mlsMessageApi.sendCommitBundle(mlsCommitBundleMapper.toDTO(commitBundle))
        }.flatMapLeft { networkFailure ->
            Either.Left(networkFailure.wrapNetworkMlsFailureIfApplicable())
        }.fold({
            throw MlsMessageRejectedException(MLSTransportFailureSerialization.serialize(it))
        }, {
            processCommitBundleEvents(it.events)
        })
    }

    private fun processCommitBundleEvents(events: List<EventContentDTO>) {
        events.forEach { eventContentDTO ->
            val event =
                MapperProvider.eventMapper(selfUserId).fromEventContentDTO(
                    LocalId.generate(),
                    eventContentDTO
                )
            if (event is Event.Conversation) {
                kaliumLogger.d("[MLSTransportProvider] Processing commit bundle events")
                localEventRepository.emitLocalEvent(event)
            }
        }
    }
}
