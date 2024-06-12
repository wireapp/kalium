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

internal object MLSPublicKeysAdapter : ColumnAdapter<Map<String, String>, String> {
    override fun decode(databaseValue: String): Map<String, String> =
        if (databaseValue.isEmpty()) {
            mapOf()
        } else {
            databaseValue.split(ITEMS_SEPARATOR).associate {
                val components = it.split(KEY_VALUE_SEPARATOR)
                components.first() to components.last()
            }
        }

    override fun encode(value: Map<String, String>): String =
        value.toList().joinToString(ITEMS_SEPARATOR) { "${it.first}$KEY_VALUE_SEPARATOR${it.second}" }

    private const val ITEMS_SEPARATOR = ";"
    private const val KEY_VALUE_SEPARATOR = ","
}
