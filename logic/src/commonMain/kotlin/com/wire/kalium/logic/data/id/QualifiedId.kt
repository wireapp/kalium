package com.wire.kalium.logic.data.id

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QualifiedID(
    @SerialName("id")
    val value: String,
    @SerialName("domain")
    val domain: String
) {
    companion object {
        const val WIRE_PRODUCTION_DOMAIN = "wire.com"
    }

    override fun toString(): String = if (domain.isEmpty()) value else "$value$VALUE_DOMAIN_SEPARATOR$domain"
}

const val VALUE_DOMAIN_SEPARATOR = "@"
val FEDERATION_REGEX = """[^@.]+@[^@.]+\.[^@]+""".toRegex()

typealias ConversationId = QualifiedID

fun String.toConversationId(fallbackDomain: String = "wire.com"): ConversationId {
    val (value, domain) = if (contains(VALUE_DOMAIN_SEPARATOR)) {
        split(VALUE_DOMAIN_SEPARATOR).let { Pair(it.first(), it.last()) }
    } else {
        Pair(this@toConversationId, fallbackDomain)
    }

    return ConversationId(
        value = value,
        domain = domain
    )
}

fun String.parseIntoQualifiedID(): QualifiedID {
    val components = split("@").filter { it.isNotBlank() }

    return when {
        components.isEmpty() -> QualifiedID(value = "", domain = "")
        components.size == 1 -> QualifiedID(value = components.first(), domain = "")
        else -> QualifiedID(value = components.first(), domain = components.last())
    }
}
