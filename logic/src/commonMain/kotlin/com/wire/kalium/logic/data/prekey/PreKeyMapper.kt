package com.wire.kalium.logic.data.prekey

import com.wire.kalium.cryptography.PreKey
import com.wire.kalium.network.api.prekey.PreKeyDTO

class PreKeyMapper {
    fun fromPreKeyDTO(preyKeyDTO: PreKeyDTO): PreKey = PreKey(id = preyKeyDTO.id, encodedData = preyKeyDTO.key)

    fun toPreKeyDTO(preKey: PreKey): PreKeyDTO = PreKeyDTO(id = preKey.id, key = preKey.encodedData)
}
