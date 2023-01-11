package com.wire.kalium.network.api.base.authenticated.message

import com.wire.kalium.protobuf.otr.UserId

interface OtrUserIdMapper {
    fun toOtrUserId(userId: String): UserId
    fun fromOtrUserId(otrUserId: UserId): String
}

expect fun provideOtrUserIdMapper(): OtrUserIdMapper
