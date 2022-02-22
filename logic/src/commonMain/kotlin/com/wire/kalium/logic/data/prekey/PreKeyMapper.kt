package com.wire.kalium.logic.data.prekey

import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.network.api.prekey.PreKeyDTO

interface PreKeyMapper {
    fun fromPreKeyDTO(preyKeyDTO: PreKeyDTO): PreKeyCrypto
    fun toPreKeyDTO(preKey: PreKeyCrypto): PreKeyDTO
}

class PreKeyMapperImpl : PreKeyMapper {
    override fun fromPreKeyDTO(preyKeyDTO: PreKeyDTO): PreKeyCrypto = PreKeyCrypto(id = preyKeyDTO.id, encodedData = preyKeyDTO.key)

    override fun toPreKeyDTO(preKey: PreKeyCrypto): PreKeyDTO = PreKeyDTO(id = preKey.id, key = preKey.encodedData)
}
