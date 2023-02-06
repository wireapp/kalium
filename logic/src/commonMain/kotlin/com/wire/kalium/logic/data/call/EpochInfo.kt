package com.wire.kalium.logic.data.call

data class EpochInfo(
    val epoch: ULong,
    val members: CallClientList,
    val sharedSecret: ByteArray
)
