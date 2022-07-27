package com.wire.kalium.logic.data.id

import com.wire.kalium.logic.data.user.UserRepository

class QualifiedIdMapper(
    private val userRepository: UserRepository?
) {
    fun fromStringToQualifiedID(id: String): QualifiedID {
        val components = id.split(VALUE_DOMAIN_SEPARATOR).filter { it.isNotBlank() }

        val count = id.count { it == VALUE_DOMAIN_SEPARATOR }
        if (id.isEmpty()) {
            QualifiedID(value = "", domain = "")
        }
        return if (count > 1) {
            val value = id.substringBeforeLast(VALUE_DOMAIN_SEPARATOR)
            val domain = id.substringAfterLast(VALUE_DOMAIN_SEPARATOR)
            QualifiedID(value = value, domain = domain)

        } else {
            val selfUserDomain = userRepository?.getSelfUserId()?.domain ?: run { "" }
            QualifiedID(value = components.first(), domain = selfUserDomain)
        }
    }
}
