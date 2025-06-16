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

package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.failure.ProteusSendMessageFailure
import com.wire.kalium.network.api.base.authenticated.message.QualifiedUserIdToClientMap
import com.wire.kalium.network.exceptions.ProteusClientsChangedError
import io.mockative.Mockable

@Mockable
interface SendMessageFailureMapper {
    fun fromDTO(error: ProteusClientsChangedError): ProteusSendMessageFailure
}

class SendMessageFailureMapperImpl : SendMessageFailureMapper {
    override fun fromDTO(error: ProteusClientsChangedError): ProteusSendMessageFailure = with(error.errorBody) {
        ProteusSendMessageFailure(
            missingClientsOfUsers = missing.fromNestedMapToSimpleMap(),
            redundantClientsOfUsers = redundant.fromNestedMapToSimpleMap(),
            deletedClientsOfUsers = deleted.fromNestedMapToSimpleMap(),
            failedClientsOfUsers = failedToConfirmClients?.fromNestedMapToSimpleMap()
        )
    }

    private fun QualifiedUserIdToClientMap.fromNestedMapToSimpleMap(): Map<QualifiedID, List<ClientId>> {
        return this.entries.flatMap { domainEntry ->
            val domain = domainEntry.key
            val userEntries = domainEntry.value
            userEntries.map { userEntry ->
                val clients = userEntry.value.map { ClientId(it) }
                val userId = UserId(value = userEntry.key, domain = domain)
                userId to clients
            }
        }.toMap()
    }
}
