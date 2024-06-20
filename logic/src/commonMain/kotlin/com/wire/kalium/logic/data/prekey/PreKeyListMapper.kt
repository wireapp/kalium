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

package com.wire.kalium.logic.data.prekey

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.base.authenticated.prekey.ListPrekeysResponse
import com.wire.kalium.network.api.authenticated.prekey.PreKeyDTO

class PreKeyListMapper(private val preKeyMapper: PreKeyMapper) {

    fun toRemoteClientPreKeyInfoTo(
        clientPreKeyInfo: Map<UserId, List<ClientId>>
    ): Map<String, Map<String, List<String>>> =
        clientPreKeyInfo.entries.groupBy { it.key.domain }
            .mapValues { domainEntry ->
                domainEntry.value.associate { userEntry ->
                    userEntry.key.value to userEntry.value.map { it.value }
                }
            }

    fun fromRemoteQualifiedPreKeyInfoMap(
        qualifiedPreKeyListResponse: Map<String, Map<String, Map<String, PreKeyDTO?>>>
    ): List<QualifiedUserPreKeyInfo> =
        qualifiedPreKeyListResponse.entries.flatMap { domainEntry ->
            domainEntry.value.mapKeys { userEntry ->
                QualifiedID(userEntry.key, domainEntry.key)
            }.mapValues { userEntry ->
                userEntry.value.mapValues { clientEntry ->
                    clientEntry.value?.let { preKeyDTO -> preKeyMapper.fromPreKeyDTO(preKeyDTO) }
                }
            }.map { entry ->
                val clientsInfo = entry.value
                    .map { clientEntry -> ClientPreKeyInfo(clientEntry.key, clientEntry.value) }
                QualifiedUserPreKeyInfo(entry.key, clientsInfo)
            }
        }

    fun fromListPrekeyResponseToUsersWithoutSessions(listPrekeysResponse: ListPrekeysResponse) =
        UsersWithoutSessions(listPrekeysResponse.failedToList
            ?.map { it.toModel() }
            ?: listOf())
}
