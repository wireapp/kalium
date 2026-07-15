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

import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.common.logger.logStructuredJson
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logic.data.user.UserId

public interface QualifiedIdMapper {
    public fun fromStringToQualifiedID(id: String): QualifiedID
}

internal class QualifiedIdMapperImpl internal constructor(
    private val selfUserId: UserId?
) : QualifiedIdMapper {
    override fun fromStringToQualifiedID(id: String): QualifiedID = id.parseQualifiedID(::selfUserDomain)

    private fun selfUserDomain(): String = selfUserId?.domain?.takeIf { it.isNotBlank() } ?: run {
        val cause = IllegalStateException("self_user_domain_missing_or_blank")
        kaliumLogger.logStructuredJson(
            level = KaliumLogLevel.ERROR,
            leadingMessage = "qualified-id mapper missing self domain",
            jsonStringKeyValues = mapOf(
                "event" to "qualified_id_mapper_missing_self_domain",
                "action" to "fallback_to_empty_domain",
                "selfUserPresent" to (selfUserId != null),
                "cause" to (cause.message ?: "unknown"),
                "stackTrace" to cause.stackTraceToString(),
            )
        )
        ""
    }
}

public fun QualifiedIdMapper(selfUserId: UserId?): QualifiedIdMapper = QualifiedIdMapperImpl(selfUserId)

public fun String.toQualifiedID(mapper: QualifiedIdMapper): QualifiedID = mapper.fromStringToQualifiedID(this)
