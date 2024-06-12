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
package com.wire.kalium.persistence.dao

import com.wire.kalium.logger.obfuscateDomain
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.persistence.dao.ManagedByEntity.WIRE
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QualifiedIDEntity(
    @SerialName("value") val value: String,
    @SerialName("domain") val domain: String
) {
    override fun toString(): String = if (domain.isEmpty()) value else "$value${VALUE_DOMAIN_SEPARATOR}$domain"

    fun toLogString(): String = if (domain.isEmpty()) {
        value.obfuscateId()
    } else {
        "${value.obfuscateId()}${VALUE_DOMAIN_SEPARATOR}${domain.obfuscateDomain()}"
    }

    companion object {
        private const val VALUE_DOMAIN_SEPARATOR = '@'
    }

}

typealias UserIDEntity = QualifiedIDEntity
typealias ConversationIDEntity = QualifiedIDEntity

/**
 * This is used to indicate if the self user (account) is managed by SCIM or Wire
 * If the user is managed by other than [WIRE], then is a read only account.
 */
enum class ManagedByEntity {
    WIRE, SCIM
}
