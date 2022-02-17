package com.wire.kalium.logic.data.prekey

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.network.api.prekey.DomainToUserIdToClientsMap
import com.wire.kalium.network.api.prekey.PreKeyDTO

interface PreKeyMapper {
    fun fromPreKeyDTO(preyKeyDTO: PreKeyDTO): PreKey

    fun toPreKeyDTO(preKey: PreKey): PreKeyDTO

    fun fromRemoteQualifiedPreKeyInfoMap(qualifiedPreKeyListResponse: Map<String, Map<String, Map<String, PreKeyDTO>>>): List<QualifiedUserPreKeyInfo>

    fun toRemoteClientPreKeyInfoTo(users: Map<QualifiedID, List<String>>): DomainToUserIdToClientsMap
}

class PreKeyMapperImpl : PreKeyMapper {
    override fun fromPreKeyDTO(preyKeyDTO: PreKeyDTO): PreKey = PreKey(id = preyKeyDTO.id, encodedData = preyKeyDTO.key)

    override fun toPreKeyDTO(preKey: PreKey): PreKeyDTO = PreKeyDTO(id = preKey.id, key = preKey.encodedData)

    override fun toRemoteClientPreKeyInfoTo(clientPreKeyInfo: Map<QualifiedID, List<String>>): DomainToUserIdToClientsMap =
        clientPreKeyInfo.mapValues { entry -> mapOf(entry.key.domain to entry.value) }
            .mapKeys { entry -> entry.key.domain }

    override fun fromRemoteQualifiedPreKeyInfoMap(qualifiedPreKeyListResponse: Map<String, Map<String, Map<String, PreKeyDTO>>>): List<QualifiedUserPreKeyInfo> =
        qualifiedPreKeyListResponse.entries.flatMap { domainEntry ->
            domainEntry.value.mapKeys { userEntry ->
                QualifiedID(domainEntry.key, userEntry.key)
            }.mapValues { userEntry ->
                userEntry.value.mapValues { clientEntry ->
                    fromPreKeyDTO(clientEntry.value)
                }
            }.map { entry ->
                val clientsInfo = entry.value.map { clientEntry -> ClientPreKeyInfo(clientEntry.key, clientEntry.value) }
                QualifiedUserPreKeyInfo(entry.key, clientsInfo)
            }
        }
}
