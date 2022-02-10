package com.wire.kalium.logic.data.prekey

import com.wire.kalium.cryptography.PreKey
import com.wire.kalium.network.api.prekey.PreKeyDTO

interface PreKeyMapper {
    fun fromPreKeyDTO(preyKeyDTO: PreKeyDTO): PreKey

    fun toPreKeyDTO(preKey: PreKey): PreKeyDTO
}

class PreKeyMapperImpl : PreKeyMapper {
    override fun fromPreKeyDTO(preyKeyDTO: PreKeyDTO): PreKey = PreKey(id = preyKeyDTO.id, encodedData = preyKeyDTO.key)

    override fun toPreKeyDTO(preKey: PreKey): PreKeyDTO = PreKeyDTO(id = preKey.id, key = preKey.encodedData)
}
