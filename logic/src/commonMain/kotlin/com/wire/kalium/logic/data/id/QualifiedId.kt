package com.wire.kalium.logic.data.id

private const val VALUE_DOMAIN_SEPARATOR = "@"

data class QualifiedID(
    val value: String,
    val domain: String
)

typealias ConversationId = QualifiedID

fun QualifiedID.asString() = if(domain.isEmpty()) value else "$value$VALUE_DOMAIN_SEPARATOR$domain"

fun String.toConversationId(): ConversationId {
    val (value, domain) = if (contains(VALUE_DOMAIN_SEPARATOR)) {
        split(VALUE_DOMAIN_SEPARATOR).let { Pair(it.first(), it.last()) }
    } else { Pair(this@toConversationId, "") }

    return ConversationId(
        value = value,
        domain = domain
    )
}
