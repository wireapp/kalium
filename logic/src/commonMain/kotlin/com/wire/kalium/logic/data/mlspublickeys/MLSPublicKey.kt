package com.wire.kalium.logic.data.mlspublickeys

import com.wire.kalium.logic.data.conversation.Conversation
import kotlin.jvm.JvmInline

data class MLSPublicKey(
    val cipherSuite: Conversation.CipherSuite,
    val key: Key,
    val keyType: KeyType

)

@JvmInline
value class Key(val value: String)

enum class KeyType { REMOVAL }
