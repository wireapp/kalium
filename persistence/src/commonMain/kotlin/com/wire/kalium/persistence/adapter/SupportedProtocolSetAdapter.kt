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
package com.wire.kalium.persistence.adapter

import app.cash.sqldelight.ColumnAdapter
import com.wire.kalium.persistence.dao.SupportedProtocolEntity

internal object SupportedProtocolSetAdapter : ColumnAdapter<Set<SupportedProtocolEntity>, String> {
    override fun decode(databaseValue: String): Set<SupportedProtocolEntity> {
        return if (databaseValue.isBlank()) {
            emptySet()
        } else {
            databaseValue.split(SEPARATOR).map { SupportedProtocolEntity.valueOf(it) }.toSet()
        }
    }

    override fun encode(value: Set<SupportedProtocolEntity>): String {
        return value.joinToString(SEPARATOR) { it.name }
    }

    private const val SEPARATOR = ","
}
