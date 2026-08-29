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
package com.wire.kalium.logic.configuration

import com.wire.kalium.persistence.config.E2EISettingsEntity
import kotlinx.datetime.Instant

public data class E2EISettings(
    public val isRequired: Boolean,
    public val discoverUrl: String?,
    public val gracePeriodEnd: Instant?,
    public val shouldUseProxy: Boolean,
    public val crlProxy: String?,
) {

    public fun toEntity(): E2EISettingsEntity = E2EISettingsEntity(
        isRequired,
        discoverUrl,
        gracePeriodEnd?.toEpochMilliseconds(),
        shouldUseProxy,
        crlProxy
    )

    public companion object {
        public fun fromEntity(entity: E2EISettingsEntity): E2EISettings = E2EISettings(
            entity.status,
            entity.discoverUrl,
            entity.gracePeriodEndMs?.let { Instant.fromEpochMilliseconds(it) },
            entity.shouldUseProxy == true,
            entity.crlProxy
        )
    }
}
