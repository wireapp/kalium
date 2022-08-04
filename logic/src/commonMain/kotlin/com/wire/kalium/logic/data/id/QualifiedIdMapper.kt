package com.wire.kalium.logic.data.id

import com.wire.kalium.logic.data.user.UserRepository

interface QualifiedIdMapper {
    fun fromStringToQualifiedID(id: String): QualifiedID
}

class QualifiedIdMapperImpl(
    private val userRepository: UserRepository?
) : QualifiedIdMapper {
    override fun fromStringToQualifiedID(id: String): QualifiedID {
        val components = id.split(VALUE_DOMAIN_SEPARATOR).filter { it.isNotBlank() }
        val count = id.count { it == VALUE_DOMAIN_SEPARATOR }
        return when {
            id.isEmpty() || components.isEmpty() -> {
                QualifiedID(value = "", domain = "")
            }
            count > 1 -> {
                val value = id.substringBeforeLast(VALUE_DOMAIN_SEPARATOR)
                val domain = id.substringAfterLast(VALUE_DOMAIN_SEPARATOR)
                QualifiedID(value = value, domain = domain)
            }
            count == 1 -> {
                QualifiedID(value = components.first(), domain = components.last())
            }
            else -> {
                val selfUserDomain = userRepository?.getSelfUserId()?.domain ?: run { "" }
                QualifiedID(value = components.first(), domain = selfUserDomain)
            }
        }
    }
}

fun String.toQualifiedID(mapper: QualifiedIdMapper): QualifiedID = mapper.fromStringToQualifiedID(this)
