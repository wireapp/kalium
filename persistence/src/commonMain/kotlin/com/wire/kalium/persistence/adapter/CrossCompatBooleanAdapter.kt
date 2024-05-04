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
import com.wire.kalium.persistence.dao.BooleanEntity

internal object CrossCompatBooleanAdapter : ColumnAdapter<BooleanEntity, Long?> {
    override fun decode(databaseValue: Long?): BooleanEntity {
        return databaseValue == 1L
    }

    override fun encode(value: BooleanEntity): Long {
        return value.toLong()
    }
}

fun Boolean.toLong(): Long {
    return when (this) {
        true -> 1
        false -> 0
    }
}
