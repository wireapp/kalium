package com.wire.kalium.logic.data.prekey

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.base.authenticated.prekey.PreKeyDTO

class PreKeyListMapper(private val preKeyMapper: PreKeyMapper) {

    // TODO(testing): unit test to be created later
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
}
