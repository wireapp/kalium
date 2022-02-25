package com.wire.kalium.logic.data.prekey.remote

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.prekey.ClientPreKeyInfo
import com.wire.kalium.logic.data.prekey.PreKeyMapper
import com.wire.kalium.logic.data.prekey.QualifiedUserPreKeyInfo
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.prekey.PreKeyDTO

class PreKeyListMapper(private val preKeyMapper: PreKeyMapper) {

    //TODO unit test to be created later
    fun toRemoteClientPreKeyInfoTo(clientPreKeyInfo: Map<UserId, List<ClientId>>): Map<String, Map<String, List<String>>> =
        clientPreKeyInfo.entries.groupBy { it.key.domain }
            .mapValues { domainEntry ->
                domainEntry.value.associate { userEntry ->
                    userEntry.key.value to userEntry.value.map { it.value }
                }
            }

    fun fromRemoteQualifiedPreKeyInfoMap(qualifiedPreKeyListResponse: Map<String, Map<String, Map<String, PreKeyDTO>>>): List<QualifiedUserPreKeyInfo> =
        qualifiedPreKeyListResponse.entries.flatMap { domainEntry ->
            domainEntry.value.mapKeys { userEntry ->
                QualifiedID(userEntry.key, domainEntry.key)
            }.mapValues { userEntry ->
                userEntry.value.mapValues { clientEntry ->
                    preKeyMapper.fromPreKeyDTO(clientEntry.value)
                }
            }.map { entry ->
                val clientsInfo = entry.value.map { clientEntry -> ClientPreKeyInfo(clientEntry.key, clientEntry.value) }
                QualifiedUserPreKeyInfo(entry.key, clientsInfo)
            }
        }
}
