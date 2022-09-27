package com.wire.kalium.logic.data.mlspublickeys

import kotlin.jvm.JvmInline

@JvmInline
value class Ed25519Key(
    val value: ByteArray
)

data class MLSPublicKey(
    val key: Ed25519Key,
    val keyType: KeyType

)

enum class KeyType { REMOVAL }
