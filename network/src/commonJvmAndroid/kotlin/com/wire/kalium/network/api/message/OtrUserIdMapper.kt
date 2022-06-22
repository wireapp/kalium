package com.wire.kalium.network.api.message

import com.wire.kalium.protobuf.otr.UserId
import pbandk.ByteArr
import java.nio.ByteBuffer
import java.util.UUID

class OtrUserIdMapper {

    fun toOtrUserId(userId: String): UserId {
        val bytes = ByteArray(USER_UID_BYTE_COUNT)
        val byteBuffer = ByteBuffer.wrap(bytes).asLongBuffer()
        val uuid = UUID.fromString(userId)
        byteBuffer.put(uuid.mostSignificantBits)
        byteBuffer.put(uuid.leastSignificantBits)
        return UserId(uuid = (ByteArr(bytes)))
    }

    fun fromOtrUserId(otrUserId: UserId): String {
        val bytes = otrUserId.uuid.array
        val byteBuffer = ByteBuffer.wrap(bytes)
        return UUID(byteBuffer.long, byteBuffer.long).toString()
    }

    companion object {
        private const val USER_UID_BYTE_COUNT = 16
    }
}
