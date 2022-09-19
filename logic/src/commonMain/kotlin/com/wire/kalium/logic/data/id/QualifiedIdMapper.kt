package com.wire.kalium.logic.data.id

import com.wire.kalium.logic.data.user.UserId

interface QualifiedIdMapper {
    fun fromStringToQualifiedID(id: String): QualifiedID
}

class QualifiedIdMapperImpl(
    private val selfUserId: UserId?
) : QualifiedIdMapper {
    override fun fromStringToQualifiedID(id: String): QualifiedID {
        val components = id.split(VALUE_DOMAIN_SEPARATOR).filter { it.isNotBlank() }
        val count = id.count { it == VALUE_DOMAIN_SEPARATOR }
        return when {
            components.isEmpty() -> {
                QualifiedID(value = "", domain = "")
            }
            count > 1 -> {
                val value = id.substringBeforeLast(VALUE_DOMAIN_SEPARATOR).removePrefix(VALUE_DOMAIN_SEPARATOR.toString())
                val domain = id.substringAfterLast(VALUE_DOMAIN_SEPARATOR).ifBlank { selfUserDomain() }
                QualifiedID(value = value, domain = domain)
            }
            count == 1 && components.size == 2 -> {
                QualifiedID(value = components.first(), domain = components.last())
            }
            else -> {
                QualifiedID(value = components.first(), domain = selfUserDomain())
            }
        }
    }

    private fun selfUserDomain(): String = selfUserId?.domain ?: ""
}

fun String.toQualifiedID(mapper: QualifiedIdMapper): QualifiedID = mapper.fromStringToQualifiedID(this)
