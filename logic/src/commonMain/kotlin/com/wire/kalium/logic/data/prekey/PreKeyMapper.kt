package com.wire.kalium.logic.data.prekey

import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.network.api.prekey.PreKeyDTO

interface PreKeyMapper {
    fun fromPreKeyDTO(preyKeyDTO: PreKeyDTO): PreKey
    fun toPreKeyDTO(preKey: PreKey): PreKeyDTO
    fun fromPreKeyCrypto(preyKeyCrypto: PreKeyCrypto): PreKey
    fun toPreKeyCrypto(preKey: PreKey): PreKeyCrypto
    fun fromPreKeyCryptoList(preyKeyCryptoList: List<PreKeyCrypto>): List<PreKey>
    fun toPreKeyCryptoList(preKeyList: List<PreKey>): List<PreKeyCrypto>
    fun fromRemoteQualifiedPreKeyInfoMap(qualifiedPreKeyListResponse: Map<String, Map<String, Map<String, PreKeyDTO>>>): List<QualifiedUserPreKeyInfo>
    fun toRemoteClientPreKeyInfoTo(users: Map<QualifiedID, List<String>>): Map<String, Map<String, List<String>>>
}

class PreKeyMapperImpl : PreKeyMapper {
    override fun fromPreKeyDTO(preyKeyDTO: PreKeyDTO): PreKey = PreKey(id = preyKeyDTO.id, key = preyKeyDTO.key)

    override fun toPreKeyDTO(preKey: PreKey): PreKeyDTO = PreKeyDTO(id = preKey.id, key = preKey.key)

    override fun fromPreKeyCrypto(preyKeyCrypto: PreKeyCrypto): PreKey = PreKey(id = preyKeyCrypto.id, key = preyKeyCrypto.encodedData)

    override fun toPreKeyCrypto(preKey: PreKey): PreKeyCrypto = PreKeyCrypto(id = preKey.id, encodedData = preKey.key)

    override fun fromPreKeyCryptoList(preyKeyCryptoList: List<PreKeyCrypto>): List<PreKey> = preyKeyCryptoList.map { fromPreKeyCrypto(it) }

    override fun toPreKeyCryptoList(preKeyList: List<PreKey>): List<PreKeyCrypto> = preKeyList.map { toPreKeyCrypto(it) }

    override fun toRemoteClientPreKeyInfoTo(clientPreKeyInfo: Map<QualifiedID, List<String>>): Map<String, Map<String, List<String>>> =
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
