package com.wire.kalium.persistence.model

import com.wire.kalium.persistence.dao.ConversationEntity
import kotlin.jvm.JvmInline

data class MLSPublicKeyEntity(
    val cipherSuite: ConversationEntity.CipherSuite,
    val key: Key,
    val keyType: KeyType
)

@JvmInline
value class Key(val value: String)

enum class KeyType { REMOVAL }
