package com.wire.kalium.logic.data.prekey

import com.wire.kalium.network.api.prekey.PreKeyDTO

class PreKeyMapper {
    fun fromPreKeyDTO(preyKeyDTO: PreKeyDTO): PreKey = PreKey(id = preyKeyDTO.id, key = preyKeyDTO.key)

    fun toPreKeyDTO(preKey: PreKey): PreKeyDTO = PreKeyDTO(id = preKey.id, key = preKey.key)
}
