package com.wire.kalium.logic.data.id

import kotlinx.serialization.Serializable

@Serializable
data class QualifiedID(
    val value: String,
    val domain: String
) {
    companion object {
        const val WIRE_PRODUCTION_DOMAIN = "wire.com"
    }

    override fun toString(): String = if (domain.isEmpty()) value else "$value$VALUE_DOMAIN_SEPARATOR$domain"
}

const val VALUE_DOMAIN_SEPARATOR = "@"

typealias ConversationId = QualifiedID

fun String.toConversationId(): ConversationId {
    val (value, domain) = if (contains(VALUE_DOMAIN_SEPARATOR)) {
        split(VALUE_DOMAIN_SEPARATOR).let { Pair(it.first(), it.last()) }
    } else {
        Pair(this@toConversationId, "")
    }

    return ConversationId(
        value = value,
        domain = domain
    )
}

fun String.parseIntoQualifiedID(): QualifiedID {
    val components = split("@")
    if (components.size < 2) throw IllegalStateException("The string trying to parse is not a valid one")
    return QualifiedID(value = components.first(), domain = components.last())
}
