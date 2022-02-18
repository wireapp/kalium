package com.wire.kalium.logic.data.prekey.remote

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.prekey.ClientPreKeyInfo
import com.wire.kalium.logic.data.prekey.QualifiedUserPreKeyInfo
import com.wire.kalium.logic.data.prekey.PreKeyMapper
import com.wire.kalium.network.api.prekey.PreKeyDTO

class PreKeyListMapper(private val preKeyMapper: PreKeyMapper) {

    fun toRemoteClientPreKeyInfoTo(clientPreKeyInfo: Map<QualifiedID, List<String>>): Map<String, Map<String, List<String>>> =
        clientPreKeyInfo.mapValues { entry -> mapOf(entry.key.domain to entry.value) }
            .mapKeys { entry -> entry.key.domain }

    fun fromRemoteQualifiedPreKeyInfoMap(qualifiedPreKeyListResponse: Map<String, Map<String, Map<String, PreKeyDTO>>>): List<QualifiedUserPreKeyInfo> =
        qualifiedPreKeyListResponse.entries.flatMap { domainEntry ->
            domainEntry.value.mapKeys { userEntry ->
                QualifiedID(domainEntry.key, userEntry.key)
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
