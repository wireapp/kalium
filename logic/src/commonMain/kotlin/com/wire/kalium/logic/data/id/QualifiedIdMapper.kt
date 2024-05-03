/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.data.id

import com.wire.kalium.logic.data.user.UserId

interface QualifiedIdMapper {
    fun fromStringToQualifiedID(id: String): QualifiedID
}

@Deprecated("Mapper should not be public and visible to consumer apps")
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
