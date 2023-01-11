package com.wire.kalium.network.api.base.authenticated.message

import com.wire.kalium.protobuf.otr.UserId
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import pbandk.ByteArr
import platform.Foundation.NSUUID

class OtrUserIdMapperImpl : OtrUserIdMapper {

    override fun toOtrUserId(userId: String): UserId {
        val uuid = NSUUID(userId)

        val nativeBytes = ByteArray(USER_UID_BYTE_COUNT)
        nativeBytes.usePinned {
            uuid.getUUIDBytes(it.addressOf(0).reinterpret())
        }
        return UserId(uuid = (ByteArr(nativeBytes)))
    }

    override fun fromOtrUserId(otrUserId: UserId): String {
        val uuid = otrUserId.uuid.array.usePinned {
            NSUUID(it.addressOf(0).reinterpret())
        }
        return uuid.UUIDString.lowercase()
    }

    companion object {
        private const val USER_UID_BYTE_COUNT = 16
    }
}

actual fun provideOtrUserIdMapper(): OtrUserIdMapper = OtrUserIdMapperImpl()
