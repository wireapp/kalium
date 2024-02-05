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

import com.wire.kalium.logger.obfuscateDomain
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
data class QualifiedID(
    @SerialName("id")
    val value: String,
    @SerialName("domain")
    val domain: String
) {
    override fun toString(): String = if (domain.isEmpty()) value else "$value$VALUE_DOMAIN_SEPARATOR$domain"

    fun toLogString(): String = if (domain.isEmpty()) {
        value.obfuscateId()
    } else {
        "${value.obfuscateId()}$VALUE_DOMAIN_SEPARATOR${domain.obfuscateDomain()}"
    }

    fun toPlainID(): PlainId = PlainId(value)

}

const val VALUE_DOMAIN_SEPARATOR = '@'
val FEDERATION_REGEX = """[^@.]+@[^@.]+\.[^@]+""".toRegex()

typealias ConversationId = QualifiedID

@JvmInline
value class GroupID(val value: String) {
    fun toLogString() = value.obfuscateId()
}

@JvmInline
value class SubconversationId(val value: String) {
    fun toLogString() = value.obfuscateId()
}

data class QualifiedClientID(
    val clientId: ClientId,
    val userId: UserId
)

typealias MessageId = String
typealias MessageButtonId = String
